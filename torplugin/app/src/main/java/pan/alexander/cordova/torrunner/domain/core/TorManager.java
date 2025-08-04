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
import static pan.alexander.cordova.torrunner.utils.thread.ThreadDelay.makeDelay;

import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository;
import pan.alexander.cordova.torrunner.domain.network.OnTorConnectionCheckedListener;
import pan.alexander.cordova.torrunner.domain.network.TorConnectionCheckerInteractor;
import pan.alexander.cordova.torrunner.utils.file.FileManager;
import pan.alexander.cordova.torrunner.utils.network.NetworkChecker;
import pan.alexander.cordova.torrunner.utils.portchecker.PortChecker;
import pan.alexander.cordova.torrunner.utils.thread.ThreadFinder;

@Singleton
public class TorManager implements OnTorConnectionCheckedListener {

    private final PortChecker portChecker;
    private final FileManager fileManager;
    private final ConfigurationRepository configuration;
    private final CoreStatus coreStatus;
    private final StarterHelper starterHelper;
    private final Restarter restarter;
    private final Killer killer;
    private final ThreadFinder threadFinder;
    private final TorRestarterReconnector torRestarterReconnector;
    private final NetworkChecker networkChecker;

    private final Lazy<TorConnectionCheckerInteractor> torConnectionCheckerInteractor;

    @Inject
    public TorManager(
            PortChecker portChecker,
            FileManager fileManager,
            ConfigurationRepository configuration,
            CoreStatus coreStatus,
            StarterHelper starterHelper,
            Restarter restarter,
            Killer killer,
            ThreadFinder threadFinder,
            TorRestarterReconnector torRestarterReconnector,
            NetworkChecker networkChecker,
            Lazy<TorConnectionCheckerInteractor> torConnectionCheckerInteractor
    ) {
        this.portChecker = portChecker;
        this.fileManager = fileManager;
        this.configuration = configuration;
        this.coreStatus = coreStatus;
        this.starterHelper = starterHelper;
        this.restarter = restarter;
        this.killer = killer;
        this.threadFinder = threadFinder;
        this.torRestarterReconnector = torRestarterReconnector;
        this.networkChecker = networkChecker;
        this.torConnectionCheckerInteractor = torConnectionCheckerInteractor;
    }

    private final ReentrantLock lock = new ReentrantLock();

    public void startTor() {

        if (lock.isLocked()) {
            return;
        }

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
                result = threadFinder.findThreadByName("TorThread");
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

        if (coreStatus.getTorState() != RUNNING || lock.isLocked()) {
            return;
        }

        new Thread(() -> {

            if (!lock.tryLock()) {
                return;
            }

            try {

                restarter.getTorRestarterRunnable().run();

                checkInternetConnection();

            } catch (Exception e) {
                loge("TorManager restartTor", e);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        }).start();
    }

    public void restartTor() {
        if (coreStatus.getTorState() != RUNNING || lock.isLocked()) {
            return;
        }

        new Thread(() -> {

            if (!lock.tryLock()) {
                return;
            }

            try {
                coreStatus.setTorState(RESTARTING);

                Thread killerThread = new Thread(killer.getTorKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (coreStatus.getTorState() != RUNNING) {
                    lock.unlock();
                    startTor();
                    checkInternetConnection();
                }

            } catch (InterruptedException e) {
                loge("TorManager restartTorFull join interrupted!", e);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        }).start();
    }

    private void clearTorLog() {
        fileManager.rewriteFile(configuration.getTorLogPath(), Collections.emptyList());
    }

    private void checkInternetConnection() {
        torConnectionCheckerInteractor.get().checkInternetConnection();
    }

    private void sendTorStartFailureToJavaScript() {
        //TODO
    }

    @Override
    public void onConnectionChecked(boolean available) {
        if (coreStatus.getTorState() == RUNNING) {
            if (available) {
                torRestarterReconnector.stopRestarterCounters();
                coreStatus.setTorConnectionAvailable(true);
            } else if (isNetworkAvailable()) {
                torRestarterReconnector.startRestarterCounter();
                coreStatus.setTorConnectionAvailable(false);
            }
        }
    }

    private boolean isNetworkAvailable() {
        return networkChecker.isNetworkAvailable(true);
    }
}
