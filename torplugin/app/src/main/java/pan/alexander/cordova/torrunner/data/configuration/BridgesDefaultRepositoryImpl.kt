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

package pan.alexander.cordova.torrunner.data.configuration

import android.os.Build
import dalvik.system.ZipPathValidator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import pan.alexander.cordova.torrunner.domain.configuration.BridgesDefaultRepository
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationUtils.interleave
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationUtils.isVanillaBridge
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationUtils.isWebTunnelBridge
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationUtils.webTunnelSniRegex
import pan.alexander.cordova.torrunner.domain.configuration.RendezvousType
import pan.alexander.cordova.torrunner.domain.configuration.SnowflakeRepository
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceRepository
import pan.alexander.cordova.torrunner.domain.sni.SniRepository
import pan.alexander.cordova.torrunner.utils.bridges.BridgeChecker
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import pan.alexander.cordova.torrunner.utils.logger.Logger.logw
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BridgesDefaultRepositoryImpl @Inject constructor(
    private val configuration: ConfigurationRepository,
    private val snowflakeRepository: SnowflakeRepository,
    private val sniRepository: SniRepository,
    private val preferences: PreferenceRepository,
    private val bridgeChecker: BridgeChecker,
) : BridgesDefaultRepository {

    private val failedBridgesAccordingTorLog by lazy { mutableListOf<String>() }

    private val autoBridgesQueue by lazy {

        val snowFlakeBridges = listOf(
            snowflakeRepository.getBridgeLines(RendezvousType.AMP_CACHE),
            snowflakeRepository.getBridgeLines(RendezvousType.AMAZON_SQS),
            snowflakeRepository.getBridgeLines(RendezvousType.CDN77)
        )
        val conjureBridges = getDefaultConjureBridges().map { listOf(it) }
        val meekLiteBridges = getDefaultMeekLiteBridges().map { listOf(it) }

        interleave(snowFlakeBridges, conjureBridges, meekLiteBridges)
    }

    private val checkBridgesQueue by lazy {

        val obfs3Bridges = getDefaultObfs3Bridges().shuffled().chunked(2)
        val obfs4Bridges = getDefaultObfs4Bridges().shuffled().chunked(2)
        val webTunnelBridges = getDefaultWebTunnelBridges().shuffled().chunked(2)
        val vanillaBridges = getDefaultVanillaBridges().shuffled()
            .chunked(2)

        interleave(webTunnelBridges, vanillaBridges, obfs3Bridges, obfs4Bridges)
    }

    override suspend fun getNextBridgesFromAutoQueue(): List<String> {
        val currentBridges = configuration.getCurrentBridges()
        val queue = autoBridgesQueue
        for (index in queue.indices) {
            val bridges = queue[index]
            if (currentBridges.size == bridges.size && currentBridges.containsAll(bridges)) {
                return if (index < queue.size - 1) {
                    queue[index + 1]
                } else {
                    queue[0]
                }
            }
        }
        logw("BridgesDefaultRepository unable to find next bridge")
        return queue[0]
    }

    override suspend fun getNextBridgesFromCheckingQueue(): List<String> {
        val checkedBridges = getLastCheckedBridges().map {
            if (it.isWebTunnelBridge()) {
                it.replace(webTunnelSniRegex, "")
            } else {
                it
            }
        }
        val queue = checkBridgesQueue
        var nextBridges = queue[0]
        for (index in queue.indices) {
            val bridges = queue[index]
            if (checkedBridges.size == bridges.size && checkedBridges.containsAll(bridges)) {
                var offset = 1
                while (index + offset < queue.size && currentCoroutineContext().isActive) {
                    nextBridges = queue[index + offset]
                    if (currentCoroutineContext().isActive
                        && sniRepository.isWhiteListSuspected()
                        && nextBridges.firstOrNull()?.isWebTunnelBridge() != true
                        && nextBridges.firstOrNull()?.isVanillaBridge() != true) {
                        offset++
                    } else {
                        break
                    }
                    if (index + offset == queue.size) {
                        nextBridges = queue[0]
                    }
                }
                break
            }
        }
        if (nextBridges.first().isWebTunnelBridge() && currentCoroutineContext().isActive) {
            val fakeSni = sniRepository.getFakeSniHosts()
            preferences.setLastSni(fakeSni)
            if (fakeSni.isNotEmpty()) {
                nextBridges = nextBridges.map {
                    "$it servername=${fakeSni.joinToString(",")}"
                }
            }
        }

        setLastCheckedBridges(nextBridges)

        return nextBridges
    }

    private fun getLastCheckedBridges(): List<String> = preferences.getLastDefaultBridges().toList()

    private fun setLastCheckedBridges(bridges: List<String>) = preferences.setLastDefaultBridges(bridges.toSet())

    override fun getCheckFailedBridges(): List<String> = failedBridgesAccordingTorLog

    override fun addCheckFailedBridge(bridgeAddress: String) {
        failedBridgesAccordingTorLog.add(bridgeAddress)
    }

    override fun clearCheckFailedBridges() {
        failedBridgesAccordingTorLog.clear()
    }

    override fun getAutoQueueLength(): Int = autoBridgesQueue.size
    override fun getCheckQueueLength(): Int = checkBridgesQueue.size

    private fun getDefaultBridges(): List<String> =
        File(configuration.getTorDefaultBridgesPath()).readLines()

    private fun getDefaultObfs4Bridges(): List<String> {
        val bridges = getDefaultBridges().filter {
            it.startsWith("obfs4")
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getObfs4BridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    private fun getDefaultObfs3Bridges(): List<String> {
        val bridges = getDefaultBridges().filter {
            it.startsWith("obfs3")
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getObfs3BridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    private fun getDefaultMeekLiteBridges(): List<String> {
        val bridges = getDefaultBridges().filter {
            it.startsWith("meek_lite")
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getMeekLiteBridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    override fun getDefaultSnowflakeBridges(): List<String> {
        val bridges = getDefaultBridges().filter {
            it.startsWith("snowflake")
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getSnowFlakeBridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    private fun getDefaultConjureBridges(): List<String> {
        val bridges = getDefaultBridges().filter {
            it.startsWith("conjure")
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getConjureBridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    private fun getDefaultWebTunnelBridges(): List<String> {
        val bridges = getDefaultBridges().filter {
            it.startsWith("webtunnel")
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getWebTunnelBridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    private fun getDefaultVanillaBridges(): List<String>  = getDefaultBridges().filter {
        val bridges = getDefaultBridges().filter {
            it.startsWith("vanilla")
        }.map {
            it.removePrefix("vanilla ")
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getOtherBridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    override fun updateDefaultBridges() = try {
        val currentDefaultBridgesFile = File(configuration.getTorDefaultBridgesPath())
        val currentDefaultBridgesFileSize = currentDefaultBridgesFile.length()

        ZipInputStream(configuration.getTorAssetStream()).use { zipInputStream ->

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ZipPathValidator.clearCallback()
            }

            var zipEntry: ZipEntry? = zipInputStream.getNextEntry()
            while (zipEntry != null) {
                val fileName = zipEntry.name
                if (fileName.endsWith("bridges_default.lst")) {
                    if (zipEntry.size != currentDefaultBridgesFileSize) {
                        FileOutputStream(currentDefaultBridgesFile).use { outputStream ->
                            copyData(zipInputStream, outputStream)
                            logi("Tor default bridges were updated!")
                        }
                    }
                    break
                }
                zipEntry = zipInputStream.nextEntry
            }
        }
    } catch (e: Exception) {
        loge("BridgesDefaultRepository updateDefaultBridges", e)
    }

    @Throws(java.lang.Exception::class)
    private fun copyData(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(8 * 1024)
        var len: Int
        while (inputStream.read(buffer).also { len = it } > 0) {
            outputStream.write(buffer, 0, len)
        }
    }

}
