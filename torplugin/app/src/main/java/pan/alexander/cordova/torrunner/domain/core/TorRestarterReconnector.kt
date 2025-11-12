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
import pan.alexander.cordova.torrunner.domain.configuration.BridgeType
import pan.alexander.cordova.torrunner.domain.configuration.BridgesCustomRepository
import pan.alexander.cordova.torrunner.domain.configuration.BridgesDefaultRepository
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.configuration.RendezvousType
import pan.alexander.cordova.torrunner.framework.ActionSender
import pan.alexander.cordova.torrunner.framework.CoreServiceActions
import pan.alexander.cordova.torrunner.utils.file.FileManager
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import pan.alexander.cordova.torrunner.utils.network.NetworkChecker
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.text.indexOf
import kotlin.text.substring

private const val DELAY_BEFORE_RESTART_TOR_SEC = 10
private const val DELAY_BEFORE_FULL_RESTART_TOR_SEC = 60
private const val MIN_DELAY_ROTATE_BRIDGE_MINUTES = 3
private const val EXTRA_DELAY_ROTATE_BRIDGE_MINUTES = 1
private const val MIN_DELAY_CHECK_BRIDGE_MINUTES = 1
private const val EXTRA_DELAY_CHECK_BRIDGE_MINUTES = 1

@ExperimentalCoroutinesApi
class TorRestarterReconnector @Inject constructor(
    dispatcherIo: CoroutineDispatcher,
    private val coreStatus: CoreStatus,
    private val configuration: ConfigurationRepository,
    private val networkChecker: NetworkChecker,
    private val fileManager: FileManager,
    private val actionSender: ActionSender,
    private val bridgesDefaultRepository: BridgesDefaultRepository,
    private val bridgesCustomRepository: BridgesCustomRepository,
    private val torCheckerManager: TorCheckerManager
) {

    private val scope by lazy {
        CoroutineScope(
            SupervisorJob() +
                    dispatcherIo.limitedParallelism(1) +
                    CoroutineName("TorRestarterReconnector")
        )
    }

    private val checkBridgesScope by lazy {
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

    @Volatile
    private var rotateBridgesCounter = 0

    @Volatile
    private var checkBridgesCounter = 0

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
                rotateBridgesCounter = 0
                makeTorProgressivePartialRestart()
            }
            if (!isRotateBridgesCounterRunning() && configuration.getCurrentBridgeType() != BridgeType.NONE) {
                startRotatingBridges()
            }
            if (!isCheckBridgesCounterRunning() && !checkBridgesScope.coroutineContext.job.children.any()) {
                val bridgeType = configuration.getCurrentBridgeType()
                if (bridgeType == BridgeType.SNOWFLAKE
                    || bridgeType == BridgeType.CONJURE
                    || bridgeType == BridgeType.MEEK_LITE) {
                    startCheckingBridges()
                }
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
                stopTorChecker()
                break
            }
            delay(
                1000L * 60 * partialRestartCounter.toDouble().pow(2).toLong()
            )// 1, 4, 9, 16, 25, 36 ... minutes
            //The counter may be 0 if we use the new auto bridge
            if (partialRestartCounter == 0) {
                continue
            }
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

            if (rotateBridgesCounter > 0) {
                logi("Stop rotating bridges")
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

    private fun isRotateBridgesCounterRunning() = rotateBridgesCounter > 0

    private fun isCheckBridgesCounterRunning() = checkBridgesCounter > 0

    private fun lockFullRestarterCounter() {
        fullRestartCounter = -1
    }

    private fun resetCounters() {
        partialRestartCounter = 0
        fullRestartCounter = 0
        rotateBridgesCounter = 0
    }

    private fun restartTor() {
        actionSender.sendIntent(CoreServiceActions.ACTION_RESTART_TOR)
    }

    private fun reloadTorConfiguration() {
        actionSender.sendIntent(CoreServiceActions.ACTION_RELOAD_TOR_CONFIGURATION)
    }

    private fun isNetworkAvailable() = networkChecker.isNetworkAvailable(true)

    private fun startRotatingBridges() = scope.launch {
        logi("Start rotating bridges")
        rotateBridgesCounter++
        while (coroutineContext.isActive) {

            if (!isNetworkAvailable()) {
                stopRestarterCounters()
                stopTorChecker()
                break
            }

            delay(
                getDelayForRotatingBridges() / 2
            )
            if (coreStatus.torLoadingPercent > 10) {
                delay(
                    getDelayForRotatingBridges() / 2
                )
            }
            if (coreStatus.torLoadingPercent > 65) {
                delay(
                    EXTRA_DELAY_ROTATE_BRIDGE_MINUTES * 60 * 1000L
                )
            }
            if (coreStatus.torLoadingPercent > 90) {
                delay(
                    EXTRA_DELAY_ROTATE_BRIDGE_MINUTES * 60 * 1000L
                )
            }

            if (coreStatus.torState == CoreState.RUNNING && !isFullRestartCounterRunning() && isNetworkAvailable()) {
                rotateBridgesCounter++
                if (configuration.getCurrentBridgeType() != BridgeType.NONE) {
                    setNextBridge()
                    partialRestartCounter = 0
                } else {
                    rotateBridgesCounter = 0
                    break
                }
            }
        }
    }

    private suspend fun setNextBridge() {
        val bridges = bridgesDefaultRepository.getNextBridgesFromAutoQueue()
        configuration.setBridges(bridges)

        for (bridge in bridges) {
            if (bridge.count { it == " "[0] } > 1) {
                logi(
                    "Try bridge: ${
                        bridge.substring(
                            0,
                            bridge.indexOf(" ", bridge.indexOf(" ") + 1)
                        )
                    }"
                )
            } else {
                logi("Try bridge: $bridge")
            }
        }
    }

    private fun startCheckingBridges() = checkBridgesScope.launch {
        logi("Start checking bridges")
        try {
            val currentBridgesToCheck = mutableListOf<String>()
            checkBridgesCounter++
            while (coroutineContext.isActive) {

                bridgesDefaultRepository.clearCheckFailedBridges()

                if (!isNetworkAvailable()) {
                    stopRestarterCounters()
                    break
                }

                if (coreStatus.torState == CoreState.RUNNING && isNetworkAvailable()) {
                    checkBridgesCounter++
                    if (configuration.getCurrentBridgeType() != BridgeType.NONE) {
                        currentBridgesToCheck.clear()
                        if (checkBridgesCounter % 2 == 0) {
                            currentBridgesToCheck.addAll(checkNextDefaultBridges())
                        } else {
                            currentBridgesToCheck.addAll(checkNextCustomBridges())
                        }
                        for (bridge in currentBridgesToCheck) {
                            val type = if (checkBridgesCounter % 2 == 0) {
                                "default"
                            } else {
                                "custom"
                            }
                            if (bridge.count { it == " "[0] } > 1) {
                                logi(
                                    "Check next $type bridge: ${
                                        bridge.substring(
                                            0,
                                            bridge.indexOf(" ", bridge.indexOf(" ") + 1)
                                        )
                                    }"
                                )
                            } else {
                                logi("Check next $type bridge: $bridge")
                            }
                        }
                    } else {
                        break
                    }
                }

                delay(
                    getDelayForCheckingBridges() / 2
                )
                if (coreStatus.torCheckerLoadingPercent > 10 && coreStatus.torCheckerLoadingPercent < 100) {
                    delay(
                        getDelayForCheckingBridges() / 2
                    )
                }
                if (coreStatus.torCheckerLoadingPercent > 65 && coreStatus.torCheckerLoadingPercent < 100) {
                    delay(
                        EXTRA_DELAY_CHECK_BRIDGE_MINUTES * 60 * 1000L
                    )
                }
                if (coreStatus.torCheckerLoadingPercent > 90 && coreStatus.torCheckerLoadingPercent < 100) {
                    delay(
                        EXTRA_DELAY_CHECK_BRIDGE_MINUTES * 60 * 1000L
                    )
                }
                if (coreStatus.torCheckerLoadingPercent == 100
                    && coreStatus.torState == CoreState.RUNNING
                    && currentBridgesToCheck.isNotEmpty()
                ) {
                    stopRestarterCounters()

                    val bridgesToUse = currentBridgesToCheck.toMutableList()
                    val failedBridgesAddresses = bridgesDefaultRepository.getCheckFailedBridges()
                    bridgesToUse.removeIf { bridge ->
                        failedBridgesAddresses.any { bridge.contains(it) }
                    }
                    if (bridgesToUse.isEmpty()) {
                        bridgesToUse.addAll(currentBridgesToCheck)
                    }

                    val bridgeType = configuration.getCurrentBridgeType()
                    if (bridgeType == BridgeType.SNOWFLAKE
                        || bridgeType == BridgeType.CONJURE
                        || bridgeType == BridgeType.MEEK_LITE) {
                        configuration.setBridges(bridgesToUse)
                        for (bridge in bridgesToUse) {
                            if (bridge.count { it == " "[0] } > 1) {
                                logi(
                                    "Use bridge: ${
                                        bridge.substring(
                                            0,
                                            bridge.indexOf(" ", bridge.indexOf(" ") + 1)
                                        )
                                    }"
                                )
                            } else {
                                logi("Use bridge: $bridge")
                            }
                        }
                    }

                    break
                }
            }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            loge("TorRestarterReconnector startCheckingBridges", e)
        } finally {
            stopTorChecker()
        }
    }

    private suspend fun checkNextDefaultBridges(): List<String> = try {
        val bridges = bridgesDefaultRepository.getNextBridgesFromCheckingQueue()
        torCheckerManager.runTorChecker(bridges)
        coreStatus.torCheckerLoadingPercent = 0
        bridges
    } catch (e: Exception) {
        loge("TorRestarterReconnector checkNextDefaultBridges", e)
        emptyList()
    }

    private suspend fun checkNextCustomBridges(): List<String> = try {
        val bridges = bridgesCustomRepository.getNextBridgesFromCheckingQueue()
        torCheckerManager.runTorChecker(bridges)
        coreStatus.torCheckerLoadingPercent = 0
        bridges
    } catch (e: Exception) {
        loge("TorRestarterReconnector checkNextCustomBridges", e)
        emptyList()
    }

    private fun stopTorChecker() {
        torCheckerManager.stopTorChecker()
        checkBridgesScope.coroutineContext.cancelChildren()
        checkBridgesCounter = 0
    }

    private fun setNextSnowFlakeBridge(): RendezvousType {
        val currentSnowflakeType = configuration.getSnowflakeBridgeType()
        val nextSnowflakeBridgeType = when (currentSnowflakeType) {
            RendezvousType.AMP_CACHE -> RendezvousType.AMAZON_SQS
            RendezvousType.AMAZON_SQS -> RendezvousType.CDN77
            RendezvousType.CDN77 -> RendezvousType.AMP_CACHE
        }
        configuration.setSnowflakeBridgeType(nextSnowflakeBridgeType)
        return nextSnowflakeBridgeType
    }

    private fun getDelayForRotatingBridges() =
        1000L * 60 * MIN_DELAY_ROTATE_BRIDGE_MINUTES * ceil(
            rotateBridgesCounter / bridgesDefaultRepository.getAutoQueueLength().toDouble()
        ).toLong()

    private fun getDelayForCheckingBridges() =
        1000L * 60 * MIN_DELAY_CHECK_BRIDGE_MINUTES * ceil(
            checkBridgesCounter / bridgesDefaultRepository.getCheckQueueLength().toDouble()
        ).toLong()
}
