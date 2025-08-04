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

package pan.alexander.cordova.torrunner.domain.core

import kotlinx.coroutines.*
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.framework.ActionSender
import pan.alexander.cordova.torrunner.framework.CoreServiceActions
import pan.alexander.cordova.torrunner.utils.file.FileManager
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import pan.alexander.cordova.torrunner.utils.network.NetworkChecker
import javax.inject.Inject
import kotlin.math.pow

private const val DELAY_BEFORE_RESTART_TOR_SEC = 10
private const val DELAY_BEFORE_FULL_RESTART_TOR_SEC = 60

@ExperimentalCoroutinesApi
class TorRestarterReconnector @Inject constructor(
    dispatcherIo: CoroutineDispatcher,
    private val coreStatus: CoreStatus,
    private val configuration: ConfigurationRepository,
    private val networkChecker: NetworkChecker,
    private val fileManager: FileManager,
    private val actionSender: ActionSender
) {

    private val scope by lazy {
        CoroutineScope(
            SupervisorJob() +
                    dispatcherIo.limitedParallelism(1) +
                    CoroutineName("TorRestarterReconnector")
        )
    }

    @Volatile
    private var fullRestartCounter = 0

    @Volatile
    private var partialRestartCounter = 0

    fun startRestarterCounter() {
        try {
            if (coreStatus.isTorReady && !isFullRestartCounterRunning() && !isFullRestartCounterLocked()) {
                stopRestarterCounters()
                makeTorDelayedFullRestart()
            } else if (!coreStatus.isTorReady && !isPartialRestartCounterRunning() && !isFullRestartCounterLocked()) {
                stopRestarterCounters()
                makeTorProgressivePartialRestart()
            } else if (!coreStatus.isTorReady && !isPartialRestartCounterRunning()) {
                cancelPreviousTasks()
                makeTorProgressivePartialRestart()
            }
        } catch (_: CancellationException) {
            resetCounters()
        } catch (e: Exception) {
            loge("TorRestarterReconnector startRestarterCounter", e)
        }
    }

    private fun makeTorProgressivePartialRestart() = scope.launch {
        logi("Start Tor partial restarter counter")
        while (coroutineContext.isActive) {
            if (isNetworkAvailable()) {
                partialRestartCounter++
            } else {
                stopRestarterCounters()
                break
            }
            delay(
                1000L * 60 * partialRestartCounter.toDouble().pow(2).toLong()
            )// 1, 4, 9, 16, 25, 36 ... minutes
            if (coreStatus.isTorReady && !isFullRestartCounterLocked()) {
                resetCounters()
                makeTorDelayedFullRestart()
                break
            } else if (isNetworkAvailable()) {
                logi("Reload Tor configuration to re-establish a connection")
                reloadTorConfiguration()
            }
        }
    }

    private fun makeTorDelayedFullRestart() = scope.launch {
        logi("Start Tor full restarter counter")
        while (coroutineContext.isActive && fullRestartCounter < DELAY_BEFORE_FULL_RESTART_TOR_SEC) {
            if (fullRestartCounter == DELAY_BEFORE_RESTART_TOR_SEC
                && coreStatus.isTorReady
                && isNetworkAvailable()
            ) {
                logi("Reload Tor configuration to re-establish a connection")
                reloadTorConfiguration()
            }
            fullRestartCounter++
            delay(1000L)
        }

        if (coreStatus.torState == CoreState.RUNNING
            && coreStatus.isTorReady
            && isNetworkAvailable()
            && coroutineContext.isActive
        ) {
            deleteTorCachedFiles()
            restartTor()
            lockFullRestarterCounter()
            logi("Restart Tor to re-establish a connection")
        } else {
            resetCounters()
            logi("Reset Tor restarter counter")
        }
    }

    private fun deleteTorCachedFiles() {
        fileManager.deleteFile(configuration.getAppDataDir() + "/tor_data/cached-microdesc-consensus")
    }

    fun stopRestarterCounters() {
        try {
            when {
                partialRestartCounter > 0 -> logi("Stop Tor partial restarter counter")
                partialRestartCounter < 0 -> logi("Reset Tor partial restarter counter")
                fullRestartCounter > 0 -> logi("Stop Tor full restarter counter")
                fullRestartCounter < 0 -> logi("Reset Tor full restarter counter")
                else -> return
            }

            cancelPreviousTasks()
            resetCounters()
        } catch (e: Exception) {
            loge("TorRestarterReconnector stopRestarterCounters", e)
        }
    }

    private fun cancelPreviousTasks() {
        scope.coroutineContext.cancelChildren()
    }

    private fun isPartialRestartCounterRunning() = partialRestartCounter > 0

    private fun isFullRestartCounterRunning() = fullRestartCounter > 0

    private fun isFullRestartCounterLocked() = fullRestartCounter < 0

    private fun lockFullRestarterCounter() {
        fullRestartCounter = -1
    }

    private fun resetCounters() {
        partialRestartCounter = 0
        fullRestartCounter = 0
    }

    private fun restartTor() {
        actionSender.sendIntent(CoreServiceActions.ACTION_RESTART_TOR)
    }

    private fun reloadTorConfiguration() {
        actionSender.sendIntent(CoreServiceActions.ACTION_RELOAD_TOR_CONFIGURATION)
    }

    private fun isNetworkAvailable() = networkChecker.isNetworkAvailable(true)
}
