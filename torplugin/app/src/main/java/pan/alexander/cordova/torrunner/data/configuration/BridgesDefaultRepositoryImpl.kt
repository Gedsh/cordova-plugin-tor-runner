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
import pan.alexander.cordova.torrunner.domain.configuration.BridgesDefaultRepository
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.configuration.RendezvousType
import pan.alexander.cordova.torrunner.domain.configuration.SnowflakeRepository
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

class BridgesDefaultRepositoryImpl @Inject constructor(
    private val configuration: ConfigurationRepository,
    private val snowflakeRepository: SnowflakeRepository
) : BridgesDefaultRepository {

    private val autoBridgesQueue by lazy {
        val queue = mutableListOf<List<String>>()

        val snowFlakeBridges = listOf(
            snowflakeRepository.getBridgeLines(RendezvousType.AMP_CACHE),
            snowflakeRepository.getBridgeLines(RendezvousType.AMAZON_SQS),
            snowflakeRepository.getBridgeLines(RendezvousType.CDN77)
        )
        val conjureBridges = getDefaultConjureBridges().map { listOf(it) }
        val meekLiteBridges = getDefaultMeekLiteBridges().map { listOf(it) }

        with(queue) {
            add(snowFlakeBridges.getOrElse(0) { emptyList() })
            add(conjureBridges.getOrElse(0) { emptyList() })
            add(snowFlakeBridges.getOrElse(1) { emptyList() })
            add(conjureBridges.getOrElse(1) { emptyList() })
            add(snowFlakeBridges.getOrElse(2) { emptyList() })
            add(conjureBridges.getOrElse(2) { emptyList() })
            add(meekLiteBridges.getOrElse(0) { emptyList() })
        }

        queue
    }

    override fun getNextBridgesFromAutoQueue(): List<String> {
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

    override fun getAutoQueueLength(): Int = autoBridgesQueue.size

    override fun getDefaultBridges(): List<String> =
        File(configuration.getTorDefaultBridgesPath()).readLines()

    override fun getDefaultObfs4Bridges(): List<String> = getDefaultBridges().filter {
        it.startsWith("obfs4")
    }

    override fun getDefaultObfs3Bridges(): List<String> = getDefaultBridges().filter {
        it.startsWith("obfs3")
    }

    override fun getDefaultMeekLiteBridges(): List<String> = getDefaultBridges().filter {
        it.startsWith("meek_lite")
    }

    override fun getDefaultSnowflakeBridges(): List<String> = getDefaultBridges().filter {
        it.startsWith("snowflake")
    }

    override fun getDefaultConjureBridges(): List<String> = getDefaultBridges().filter {
        it.startsWith("conjure")
    }

    override fun getDefaultWebTunnelBridges(): List<String> = getDefaultBridges().filter {
        it.startsWith("webtunnel")
    }

    override fun getDefaultVanillaBridges(): List<String> {
        TODO("Not yet implemented")
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
