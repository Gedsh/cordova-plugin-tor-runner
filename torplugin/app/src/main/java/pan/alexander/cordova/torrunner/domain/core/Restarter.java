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

import static pan.alexander.cordova.torrunner.utils.logger.Logger.loge;

import com.jrummyapps.android.shell.Shell;

import java.io.File;
import java.util.List;

import javax.inject.Inject;

import pan.alexander.cordova.torrunner.framework.ActionSender;
import pan.alexander.cordova.torrunner.framework.ConfigurationManager;
import pan.alexander.cordova.torrunner.framework.CoreServiceActions;
import pan.alexander.cordova.torrunner.utils.file.FileManager;

public class Restarter {

    private final FileManager fileManager;
    private final ConfigurationManager configuration;
    private final ActionSender actionSender;

    @Inject
    public Restarter(
            FileManager fileManager,
            ConfigurationManager configuration,
            ActionSender actionSender
    ) {
        this.fileManager = fileManager;
        this.configuration = configuration;
        this.actionSender = actionSender;
    }

    public void restartTor() {
        actionSender.sendIntent(CoreServiceActions.ACTION_RESTART_TOR);
    }

    public Runnable getTorRestarterRunnable() {

        String torPid = readPidFile(configuration.getTorPidPath());

        return () -> restartModule(configuration.getTorPath(), torPid);

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

    private synchronized void restartModule(String module, String pid) {
        if (module.contains("/")) {
            module = module.substring(module.lastIndexOf("/"));
        }

        String[] preparedCommands = prepareRestartCommand(module, pid);

        if (!pid.isEmpty()) {
            killWithPid(pid);
        } else {
            killWithSH(module, preparedCommands);
        }
    }

    private void killWithPid(String pid) {
        try {
            android.os.Process.sendSignal(Integer.parseInt(pid), 1);
        } catch (Exception e) {
            loge("Restarter killWithPid", e);
        }
    }

    private void killWithSH(String module, String[] commands) {
        try {
            Shell.SH.run(commands);
        } catch (Exception e) {
            loge("Restart " + module + " without root", e);
        }
    }

    private String[] prepareRestartCommand(String module, String pid) {
        String[] result;

        if (pid.isEmpty()) {
            String killStringToyBox = "toybox pkill -SIGHUP " + module + " || true";
            result = new String[]{
                    killStringToyBox
            };
        } else {
            String killString = "kill -s SIGHUP " + pid + " || true";
            result = new String[]{
                    killString
            };
        }

        return result;
    }

}
