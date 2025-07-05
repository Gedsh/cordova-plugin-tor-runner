/*
    This file is part of Cordova Plugin Tor Runner.

    Cordova Plugin Tor Runner is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Cordova Plugin Tor Runner is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Cordova Plugin Tor Runner.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.cordova.torrunner.domain.core;

import static pan.alexander.cordova.torrunner.domain.core.CoreState.RESTARTING;
import static pan.alexander.cordova.torrunner.domain.core.CoreState.RUNNING;
import static pan.alexander.cordova.torrunner.domain.core.CoreState.STOPPED;
import static pan.alexander.cordova.torrunner.domain.core.CoreState.STOPPING;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.loge;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.logi;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.logw;

import com.jrummyapps.android.shell.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;

import pan.alexander.cordova.torrunner.framework.ActionSender;
import pan.alexander.cordova.torrunner.framework.ConfigurationManager;
import pan.alexander.cordova.torrunner.utils.file.FileManager;

public class Killer {

    private final ConfigurationManager configuration;
    private final ActionSender actionSender;
    private final FileManager fileManager;

    private final CoreStatus coreStatus;

    private final ReentrantLock reentrantLock;

    private static Thread torThread;

    @Inject
    Killer(
            ConfigurationManager configuration,
            ActionSender actionSender,
            FileManager fileManager,
            CoreStatus coreStatus
    ) {
        this.configuration = configuration;
        this.actionSender = actionSender;
        this.fileManager = fileManager;
        this.coreStatus = coreStatus;
        reentrantLock = new ReentrantLock();
    }

    private void sendResultIntent() {
        //TODO
    }

    private void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            loge("Killer makeDelay interrupted!", e);
        }
    }

    void setTorThread(Thread torThread) {
        Killer.torThread = torThread;
    }

    Thread getTorThread() {
        return torThread;
    }


    public Runnable getTorKillerRunnable() {
        return () -> {

            if (coreStatus.getTorState() != RESTARTING) {
                coreStatus.setTorState(STOPPING);
            }

            reentrantLock.lock();

            try {
                String torPid = readPidFile(configuration.getTorPidPath());

                boolean result = doThreeAttemptsToStopModule(
                        configuration.getTorPath(),
                        torPid,
                        torThread
                );

                if (!result && torThread != null && torThread.isAlive()) {
                    logw("Killer cannot stop Tor. Stop with interrupt thread!");
                    makeDelay(5);
                    stopModuleWithInterruptThread(torThread);
                } else if (!result) {
                    logw("Killer cannot stop Tor. Thread is null");
                }

                if (torThread != null && torThread.isAlive()) {

                    if (coreStatus.getTorState() != RESTARTING) {
                        //ModulesAux.saveTorStateRunning(true); TODO
                        makeDelay(1);
                        sendResultIntent();
                    }

                    coreStatus.setTorState(RUNNING);

                    loge("Killer cannot stop Tor!");
                } else {

                    if (coreStatus.getTorState() != RESTARTING) {
                        //ModulesAux.saveTorStateRunning(false); TODO
                        coreStatus.setTorState(STOPPED);
                        makeDelay(1);
                        sendResultIntent();
                    }
                }
            } catch (Exception e) {
                loge("Killer getTorKillerRunnable", e);
            } finally {
                reentrantLock.unlock();
            }

        };
    }

    private boolean killModule(
            String module,
            String pid,
            Thread thread,
            String signal,
            int delaySec
    ) {
        boolean result = false;

        if (module.contains("/")) {
            module = module.substring(module.lastIndexOf("/"));
        }

        List<String> preparedCommands = prepareKillCommands(module, pid, signal);

        if (!pid.isEmpty()) {
            killWithPid(signal, pid, delaySec);
        }

        if (thread != null) {
            result = !thread.isAlive();
        }

        List<String> shellResult = null;
        if (!result) {
            shellResult = killWithSH(module, preparedCommands, delaySec);
        }

        if (thread != null) {
            result = !thread.isAlive();
        }

        if (shellResult != null) {
            logi("Kill " + module + ": result " + result + "\n" + shellResult);
        } else {
            logi("Kill " + module + ": result " + result);
        }

        return result;
    }

    private void killWithPid(String signal, String pid, int delay) {
        try {
            if (signal.isEmpty()) {
                android.os.Process.sendSignal(Integer.parseInt(pid), 15);
            } else {
                android.os.Process.killProcess(Integer.parseInt(pid));
            }
            makeDelay(delay);
        } catch (Exception e) {
            loge("Killer killWithPid", e);
        }
    }

    private List<String> killWithSH(String module, List<String> commands, int delay) {
        List<String> shellResult = null;
        try {
            shellResult = Shell.SH.run(commands.toArray(new String[0])).stdout;
            makeDelay(delay);
        } catch (Exception e) {
            loge("Kill " + module + " without root", e);
        }
        return shellResult;
    }

    //kill default signal SIGTERM - 15, SIGKILL -9, SIGQUIT - 3
    private List<String> prepareKillCommands(String module, String pid, String signal) {
        List<String> result;

        if (pid.isEmpty()) {
            String killStringToyBox = "toybox pkill " + module + " || true";
            String killString = "pkill " + module + " || true";
            if (!signal.isEmpty()) {
                killStringToyBox = "toybox pkill -" + signal + " " + module + " || true";
                killString = "pkill -" + signal + " " + module + " || true";
            }

            result = new ArrayList<>(Arrays.asList(
                    killStringToyBox,
                    killString
            ));
        } else {
            String killAllStringToolBox = "toolbox kill " + pid + " || true";
            String killStringToyBox = "toybox kill " + pid + " || true";
            String killString = "kill " + pid + " || true";
            if (!signal.isEmpty()) {
                killAllStringToolBox = "toolbox kill -s " + signal + " " + pid + " || true";
                killStringToyBox = "toybox kill -s " + signal + " " + pid + " || true";
                killString = "kill -s " + signal + " " + pid + " || true";
            }

            result = new ArrayList<>(Arrays.asList(
                    killAllStringToolBox,
                    killStringToyBox,
                    killString
            ));
        }

        return result;
    }

    private boolean doThreeAttemptsToStopModule(
            String modulePath,
            String pid,
            Thread thread
    ) {
        boolean result = false;
        int attempts = 0;
        while (attempts < 3 && !result) {
            if (attempts < 2) {
                result = killModule(modulePath, pid, thread, "", attempts + 2);
            } else {
                result = killModule(modulePath, pid, thread, "SIGKILL", attempts + 1);
            }

            attempts++;
        }
        return result;
    }

    private boolean stopModuleWithInterruptThread(Thread thread) {
        boolean result = false;
        int attempts = 0;

        try {
            while (attempts < 3 && !result) {
                if (thread != null && thread.isAlive()) {
                    thread.interrupt();
                    makeDelay(3);
                }

                if (thread != null) {
                    result = !thread.isAlive();
                }

                attempts++;
            }
        } catch (Exception e) {
            loge("Kill with interrupt thread", e);
        }

        return result;
    }

    private String readPidFile(String path) {
        String pid = "";

        File file = new File(path);
        if (file.isFile()) {
            List<String> lines = fileManager.readFile(path);
            if (lines.isEmpty()) {
                return pid;
            }

            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    pid = line.trim();
                    break;
                }
            }
        }
        return pid;
    }
}
