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

    Copyright 2025-2026 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.cordova.torrunner.domain.core;

import static pan.alexander.cordova.torrunner.utils.logger.Logger.loge;
import static pan.alexander.cordova.torrunner.utils.thread.ThreadDelay.makeDelay;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;
import javax.inject.Singleton;

import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository;
import pan.alexander.cordova.torrunner.utils.thread.ThreadFinder;

@Singleton
public class TorCheckerManager {

    private final StarterHelper starterHelper;
    private final Killer killer;
    private final ThreadFinder threadFinder;
    private final ConfigurationRepository configuration;

    @Inject
    public TorCheckerManager(
            StarterHelper starterHelper,
            Killer killer,
            ThreadFinder threadFinder,
            ConfigurationRepository configuration
    ) {
        this.starterHelper = starterHelper;
        this.killer = killer;
        this.threadFinder = threadFinder;
        this.configuration = configuration;
    }

    private final ReentrantLock lock = new ReentrantLock();

    public void runTorChecker(List<String> bridges) {

        if (lock.isLocked()) {
            return;
        }

        new Thread(() -> {

            if (!lock.tryLock()) {
                return;
            }

            try {
                Thread previousTorThread = checkPreviouslyRunningTorModule();

                if (previousTorThread != null && previousTorThread.isAlive()) {

                    killer.setTorCheckerThread(previousTorThread);

                    Thread killerThread = new Thread(killer.getTorCheckerKillerRunnable());
                    killerThread.start();

                    while (killerThread.isAlive()) {
                        killerThread.join();
                    }

                    makeDelay(5);
                }

                configuration.deleteBridgesFromStateFile(configuration.getTorCheckerStateFilePath());

                Thread torThread = new Thread(starterHelper.getTorCheckerStarterRunnable(bridges));
                torThread.setName("TorCheckerThread");
                torThread.setDaemon(false);
                try {
                    torThread.setPriority(Thread.NORM_PRIORITY);
                } catch (SecurityException e) {
                    loge("TorCheckerManager startTorChecker", e);
                }
                torThread.start();

                killer.setTorCheckerThread(torThread);

            } catch (Exception e) {
                loge("Tor checker was unable to start", e);
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
            result = killer.getTorCheckerThread();
            if (result == null) {
                result = threadFinder.findThreadByName("TorCheckerThread");
            }
        } catch (Exception e) {
            loge("checkPreviouslyRunningTorCheckerModule", e);
        }

        return result;
    }

    public void stopTorChecker() {
        new Thread(killer.getTorCheckerKillerRunnable()).start();
    }
}
