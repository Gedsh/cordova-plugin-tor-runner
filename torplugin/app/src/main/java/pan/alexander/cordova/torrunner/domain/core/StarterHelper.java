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
import static pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_TOR;
import static pan.alexander.cordova.torrunner.utils.Constants.NUMBER_REGEX;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.loge;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.logi;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.logw;

import android.text.TextUtils;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import pan.alexander.cordova.torrunner.App;
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository;
import pan.alexander.cordova.torrunner.domain.installer.Installer;
import pan.alexander.cordova.torrunner.framework.ActionSender;
import pan.alexander.cordova.torrunner.utils.file.FileManager;
import pan.alexander.cordova.torrunner.utils.portchecker.PortChecker;

public class StarterHelper implements ProcessStarter.OnStdOutputListener {

    private final CoreStatus coreStatus;

    private final ConfigurationRepository configuration;
    private final FileManager fileManager;
    private final PortChecker portChecker;
    private final Installer installer;
    private final Restarter restarter;
    private final ActionSender actionSender;
    @Inject
    public StarterHelper(
            ConfigurationRepository configuration,
            CoreStatus coreStatus,
            FileManager fileManager,
            PortChecker portChecker,
            Installer installer,
            Restarter restarter,
            ActionSender actionSender
    ) {
        this.configuration = configuration;
        this.coreStatus = coreStatus;
        this.fileManager = fileManager;
        this.portChecker = portChecker;
        this.installer = installer;
        this.restarter = restarter;
        this.actionSender = actionSender;
    }

    Runnable getTorStarterRunnable() {
        return () -> {

            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            String torCmdString;
            final CommandResult shellResult;
            List<String> lines = readTorConfiguration();

            List<String> newLines = new ArrayList<>(lines);

            correctObfsModulePath(newLines);

            //checkTorPortsForBusyness(newLines); TODO

            boolean webTunnelUsed = isWebTunnelBridgesUsed(newLines);

            if (lines.size() != newLines.size() || !new HashSet<>(lines).containsAll(newLines)) {
                saveTorConfiguration(newLines);
            }

            torCmdString = configuration.getTorPath()
                    + " -f " + configuration.getTorConfPath()
                    + " -pidfile " + configuration.getTorPidPath();
            String fakeHosts = getFakeSniHosts();
            if (!fakeHosts.isEmpty() && !webTunnelUsed) {
                torCmdString += " -fake-hosts " + fakeHosts;
            }

            logi("Tor is listening on port " + configuration.getTorSocksPort());

            ProcessStarter starter = new ProcessStarter(configuration.getNativeLibPath());
            starter.setStdOutputListener(this);
            shellResult = starter.startProcess(torCmdString);

            if (shellResult.isSuccessful()) {
                if (coreStatus.getTorState() == RUNNING) {
                    coreStatus.setTorState(STOPPED);
                }
            } else {

                if (coreStatus.getTorState() == RESTARTING) {
                    return;
                }

                if (coreStatus.getTorState() != STOPPING && coreStatus.getTorState() != STOPPED) {
                    resetTorConfiguration();
                }

                loge("Error Tor: " + shellResult.exitCode
                        + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                logNativeCrash();

                if (!App.getInstance().isAppForeground() && coreStatus.getTorState() == RUNNING) {
                    if (coreStatus.isTorReady()) {
                        restarter.restartTor();
                        logw("Trying to restart Tor");
                    }
                } else {
                    coreStatus.setTorState(STOPPED);
                    sendTorStoppedToJavaScript();
                }

            }

            coreStatus.setTorReady(false);

            Thread.currentThread().interrupt();
        };
    }

    Runnable getReverseProxyStarterRunnable() {
        return () -> {

            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            String reverseProxyCmdString;
            final CommandResult shellResult;

            //checkProxyPortForBusyness(newLines); TODO

            reverseProxyCmdString = configuration.getReverseProxyPath()
                    + " -proxyport " + configuration.getReverseProxyDefaultPort()
                    + " -sockport " + configuration.getTorSocksPort()
                    + " -pidfile " + configuration.getReverseProxyPidPath();

            shellResult = new ProcessStarter(configuration.getNativeLibPath())
                    .startProcess(reverseProxyCmdString);


            if (shellResult.isSuccessful()) {
                logi("Reverse proxy stopped");
            } else {

                loge("Error Reverse-proxy: " + shellResult.exitCode
                        + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                logNativeCrash();

                if (coreStatus.getTorState() == RUNNING) {
                    actionSender.sendIntent(ACTION_STOP_TOR);
                }

            }

            Thread.currentThread().interrupt();
        };
    }

    private void correctObfsModulePath(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String newLine = line;
            if (line.contains("ClientTransportPlugin ") && line.contains("/libobfs4proxy.so")) {
                newLine = line.replaceAll("/.+?/libobfs4proxy.so", configuration.getObfsPath());
            } else if (line.contains("ClientTransportPlugin ") && line.contains("/libsnowflake.so")) {
                newLine = line.replaceAll("/.+?/libsnowflake.so", configuration.getSnowflakePath());
            } else if (line.contains("ClientTransportPlugin ") && line.contains("/libwebtunnel.so")) {
                newLine = line.replaceAll("/.+?/libwebtunnel.so", configuration.getWebTunnelPath());
            }
            if (!newLine.equals(line)) {
                lines.set(i, newLine);
                logi("StarterHelper Tor obfs module path is corrected");
            }
        }
    }

    private void checkTorPortsForBusyness(List<String> lines) {

        String socksPort = configuration.getTorDefaultSocksPort() + "";

        if (socksPort.matches(NUMBER_REGEX) && portChecker.isPortBusy(socksPort)) {
            fixTorProxyPort(lines, socksPort);
        }

    }

    private boolean isWebTunnelBridgesUsed(List<String> torConf) {
        boolean bridgesUsed = false;
        boolean webTunnelUsed = false;

        for (String line : torConf) {
            if (line.contains("UseBridges 1")) {
                bridgesUsed = true;
            } else if (bridgesUsed && line.startsWith("Bridge webtunnel")) {
                webTunnelUsed = true;
                break;
            }
        }
        return webTunnelUsed;
    }

    private void fixTorProxyPort(List<String> lines, String proxyPort) {
        String port = portChecker.getFreePort(proxyPort);

        if (port.equals(proxyPort)) {
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("SOCKSPort") && line.contains(proxyPort)) {
                line = line.replace(proxyPort, port);
                lines.set(i, line);
            }
        }
    }

    private List<String> readTorConfiguration() {
        return fileManager.readFile(configuration.getTorConfPath());
    }

    private void saveTorConfiguration(List<String> lines) {
        fileManager.rewriteFile(configuration.getTorConfPath(), lines);
    }

    private String getFakeSniHosts() {
        Set<String> hosts = new HashSet<>();
        hosts.add("play.googleapis.com");
        hosts.add("drive.google.com");
        hosts.add("cdn.ampproject.org");
        hosts.add("api.github.com");
        hosts.add("ajax.aspnetcdn.com");
        hosts.add("verizon.com");
        hosts.add("eset.com");
        return TextUtils.join(",", hosts);
    }

    private void logNativeCrash() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());
            String time = sdf.format(new Date(System.currentTimeMillis() - 3000));
            Process process = new ProcessBuilder(
                    "logcat",
                    "-d",
                    "*:F",
                    "-t",
                    time
            ).start();
            try (InputStreamReader is = new InputStreamReader(process.getInputStream());
                 BufferedReader br = new BufferedReader(is)) {
                String line = br.readLine();
                while (line != null) {
                    loge(line);
                    line = br.readLine();
                }
            }
        } catch (Exception e) {
            loge("StarterHelper logNativeCrash", e);
        }
    }

    private void resetTorConfiguration() {
        installer.reinstallTor();
    }

    private void sendTorStoppedToJavaScript() {
        //TODO
    }

    @Override
    public void onStdOutput(@NotNull String stdout) {
        if(stdout.endsWith("Bootstrapped 100% (done): Done")) {
            coreStatus.setTorReady(true);
        }
    }
}
