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
import static pan.alexander.cordova.torrunner.utils.logger.Logger.logw;
import static pan.alexander.cordova.torrunner.utils.thread.ThreadDelay.makeDelay;

import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;
import javax.inject.Singleton;

import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository;
import pan.alexander.cordova.torrunner.utils.portchecker.PortChecker;
import pan.alexander.cordova.torrunner.utils.thread.ThreadFinder;

@Singleton
public class ReverseProxyManager {

    private static final int START_DELAY_SEC = 30;

    private final CoreStatus coreStatus;
    private final PortChecker portChecker;
    private final ConfigurationRepository configuration;
    private final StarterHelper starterHelper;
    private final Killer killer;
    private final ThreadFinder threadFinder;

    @Inject
    public ReverseProxyManager(
            PortChecker portChecker,
            ConfigurationRepository configuration,
            CoreStatus coreStatus,
            StarterHelper starterHelper,
            Killer killer,
            ThreadFinder threadFinder
    ) {
        this.portChecker = portChecker;
        this.configuration = configuration;
        this.coreStatus = coreStatus;
        this.starterHelper = starterHelper;
        this.killer = killer;
        this.threadFinder = threadFinder;
    }

    private final ReentrantLock lock = new ReentrantLock();

    public void startProxy() {

        if (lock.isLocked()) {
            return;
        }

        new Thread(() -> {

            if (!lock.tryLock()) {
                return;
            }

            int counter = 0;
            while (coreStatus.getTorState() != CoreState.RUNNING && counter < START_DELAY_SEC) {
                counter ++;
                makeDelay(1);
            }

            try {
                Thread previousReverseProxyThread = checkPreviouslyRunningReverseProxyModule();

                if (previousReverseProxyThread != null && previousReverseProxyThread.isAlive()) {
                    changeReverseProxyStatus(previousReverseProxyThread);
                    return;
                }

                if (stopReverseProxyIfPortIsBusy()) {
                    changeReverseProxyStatus(killer.getReverseProxyThread());
                    logw("Reverse Proxy port " + configuration.getReverseProxyDefaultPort() + " is busy");
                    return;
                }

                Thread reverseProxyThread = new Thread(starterHelper.getReverseProxyStarterRunnable());
                reverseProxyThread.setName("ReverseProxyThread");
                reverseProxyThread.setDaemon(false);
                try {
                    reverseProxyThread.setPriority(Thread.NORM_PRIORITY);
                } catch (SecurityException e) {
                    loge("Reverse Proxy start", e);
                }
                reverseProxyThread.start();

                changeReverseProxyStatus(reverseProxyThread);

            } catch (Exception e) {
                loge("Reverse Proxy was unable to start", e);
                sendReverseProxyStartFailureToJavaScript();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        }).start();

    }

    private Thread checkPreviouslyRunningReverseProxyModule() {

        Thread result = null;

        try {
            result = threadFinder.findThreadByName("ReverseProxyThread");
        } catch (Exception e) {
            loge("Tor manager checkPreviouslyRunningReverseProxyModule", e);
        }

        return result;
    }

    private void changeReverseProxyStatus(final Thread reverseProxyThread) {

        makeDelay(2);

        if (reverseProxyThread.isAlive()) {
            if (killer != null) {
                killer.setReverseProxyThread(reverseProxyThread);
            }
        }
    }

    private boolean stopReverseProxyIfPortIsBusy() {
        boolean stopRequired = portChecker.isPortBusy(configuration.getReverseProxyDefaultPort() + "");

        if (stopRequired) {
            try {

                Thread killerThread = new Thread(killer.getReverseProxyKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                stopRequired = portChecker.isPortBusy(configuration.getReverseProxyDefaultPort() + "");
                if (stopRequired) {
                    return true;
                }

            } catch (InterruptedException e) {
                loge("TorManager stopReverseProxyIfPortsIsBusy join interrupted!", e);
            }
        }
        return false;
    }

    public void stopProxy() {
        new Thread(killer.getReverseProxyKillerRunnable()).start();
    }

    private void sendReverseProxyStartFailureToJavaScript() {
        //TODO
    }
}
