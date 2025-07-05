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
import static pan.alexander.cordova.torrunner.domain.core.CoreState.STARTING;
import static pan.alexander.cordova.torrunner.domain.core.CoreState.STOPPED;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.loge;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.logi;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;

import pan.alexander.cordova.torrunner.framework.ConfigurationManager;
import pan.alexander.cordova.torrunner.utils.file.FileManager;
import pan.alexander.cordova.torrunner.utils.portchecker.PortChecker;

public class TorManager {

    private final PortChecker portChecker;
    private final FileManager fileManager;
    private final ConfigurationManager configuration;
    private final CoreStatus coreStatus;
    private final StarterHelper starterHelper;
    private final Restarter restarter;
    private final Killer killer;

    @Inject
    public TorManager(
            PortChecker portChecker,
            FileManager fileManager,
            ConfigurationManager configuration,
            CoreStatus coreStatus,
            StarterHelper starterHelper,
            Restarter restarter,
            Killer killer
    ) {
        this.portChecker = portChecker;
        this.fileManager = fileManager;
        this.configuration = configuration;
        this.coreStatus = coreStatus;
        this.starterHelper = starterHelper;
        this.restarter = restarter;
        this.killer = killer;
    }

    private final ReentrantLock lock = new ReentrantLock();

    public void startTor() {

        new Thread(() -> {

            if (!lock.tryLock()) {
                return;
            }

            if (coreStatus.getTorState() == STOPPED) {
                coreStatus.setTorState(STARTING);
            }

            try {
                Thread previousTorThread = checkPreviouslyRunningTorModule();

                if (previousTorThread != null && previousTorThread.isAlive()) {
                    changeTorStatus(previousTorThread);
                    return;
                }

                if (stopTorIfPortsIsBusy()) {
                    changeTorStatus(killer.getTorThread());
                    return;
                }

                clearTorLog();

                Thread torThread = new Thread(starterHelper.getTorStarterRunnable());
                torThread.setName("TorThread");
                torThread.setDaemon(false);
                try {
                    torThread.setPriority(Thread.NORM_PRIORITY);
                } catch (SecurityException e) {
                    loge("TorManager startTor", e);
                }
                torThread.start();

                changeTorStatus(torThread);

            } catch (Exception e) {
                loge("Tor was unable to start", e);
                sendTorStartFailureToJavaScript();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        }).start();

    }

    private Thread checkPreviouslyRunningTorModule() {

        Thread result = null;

        try {
            if (coreStatus.getTorState() != RESTARTING) {
                result = findThreadByName("TorThread");
            }
        } catch (Exception e) {
            loge("checkPreviouslyRunningTorModule", e);
        }

        return result;
    }

    private void changeTorStatus(final Thread torThread) {

        makeDelay(2);

        if (torThread.isAlive()) {

            coreStatus.setTorState(RUNNING);

            if (killer != null) {
                killer.setTorThread(torThread);
            }

            checkInternetConnection();
        } else {
            coreStatus.setTorState(STOPPED);
        }
    }

    private boolean stopTorIfPortsIsBusy() {
        boolean stopRequired = portChecker.isPortBusy(configuration.getTorDefaultSocksPort() + "");

        if (stopRequired) {
            try {
                coreStatus.setTorState(RESTARTING);

                Thread killerThread = new Thread(killer.getTorKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (coreStatus.getTorState() == RUNNING) {
                    return true;
                }

                coreStatus.setTorState(STARTING);

            } catch (InterruptedException e) {
                loge("TorManager restartTor join interrupted!", e);
            }
        }
        return false;
    }

    public void stopTor() {
        new Thread(killer.getTorKillerRunnable()).start();
    }

    public void reloadTorConfiguration() {

        if (coreStatus.getTorState() != RUNNING) {
            return;
        }

        new Thread(() -> {
            try {

                restarter.getTorRestarterRunnable().run();

                checkInternetConnection();

            } catch (Exception e) {
                loge("TorManager restartTor", e);
            }

        }).start();
    }

    public void restartTor() {
        if (coreStatus.getTorState() != RUNNING) {
            return;
        }

        new Thread(() -> {
            try {
                coreStatus.setTorState(RESTARTING);

                Thread killerThread = new Thread(killer.getTorKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (coreStatus.getTorState() != RUNNING) {
                    startTor();
                    checkInternetConnection();
                }

            } catch (InterruptedException e) {
                loge("TorManager restartTorFull join interrupted!", e);
            }

        }).start();
    }

    private void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            loge("TorManager makeDelay interrupted!", e);
        }
    }

    public Thread findThreadByName(String threadName) {
        Thread currentThread = Thread.currentThread();
        ThreadGroup threadGroup = getRootThreadGroup(currentThread);
        int allActiveThreads = threadGroup.activeCount();
        Thread[] allThreads = new Thread[allActiveThreads];
        threadGroup.enumerate(allThreads);

        for (Thread thread : allThreads) {
            String name = "";
            if (thread != null) {
                name = thread.getName();
            }
            //logi("Current threads " + name);
            if (name.equals(threadName)) {
                logi("Found old module thread " + name);
                return thread;
            }
        }

        return null;
    }

    private ThreadGroup getRootThreadGroup(Thread thread) {
        ThreadGroup rootGroup = thread.getThreadGroup();
        while (rootGroup != null) {
            ThreadGroup parentGroup = rootGroup.getParent();
            if (parentGroup == null) {
                break;
            }
            rootGroup = parentGroup;
        }
        return rootGroup;
    }

    private void clearTorLog() {
        fileManager.rewriteFile(configuration.getTorLogPath(), Collections.emptyList());
    }

    private void checkInternetConnection() {
        //TODO
    }

    private void sendTorStartFailureToJavaScript() {
        //TODO
    }
}
