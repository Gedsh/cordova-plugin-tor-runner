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

import org.json.JSONObject
import pan.alexander.cordova.torrunner.domain.configuration.BridgeType
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.configuration.RendezvousType
import pan.alexander.cordova.torrunner.domain.configuration.SnowflakeRepository
import pan.alexander.cordova.torrunner.domain.core.TorMode
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceRepository
import pan.alexander.cordova.torrunner.framework.ActionSender
import pan.alexander.cordova.torrunner.framework.ConfigurationManager
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_RESTART_TOR
import pan.alexander.cordova.torrunner.utils.Constants.MAX_PORT_NUMBER
import pan.alexander.cordova.torrunner.utils.file.FileManager
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import java.io.File
import java.lang.Exception
import javax.inject.Inject
import kotlin.text.matches
import kotlin.text.substringAfter
import kotlin.text.toInt

class ConfigurationRepositoryImpl @Inject constructor(
    private val configurationManager: ConfigurationManager,
    private val preferences: PreferenceRepository,
    private val snowflakeRepository: SnowflakeRepository,
    private val fileManager: FileManager,
    private val actionSender: ActionSender
) : ConfigurationRepository {

    private val numberRegex by lazy { Regex("\\d{1,5}") }

    override fun getAppDataDir() = configurationManager.appDataDir

    override fun getLogsDir() = configurationManager.logsDir

    override fun isTorConfigurationAvailable() = File(configurationManager.torConfPath).isFile

    override fun getTorLogPath() = configurationManager.torLogPath

    override fun getNativeLibPath() = configurationManager.nativeLibPath

    override fun getTorPath() = configurationManager.torPath

    override fun getObfsPath() = configurationManager.obfsPath

    override fun getSnowflakePath() = configurationManager.snowflakePath

    override fun getWebTunnelPath() = configurationManager.webTunnelPath

    override fun getTorSocksPort() = getTorConfiguration().find {
        it.first == "SOCKSPort"
    }?.second?.substringAfterLast(":")
        ?.takeIf {
            it.matches(numberRegex)
        }?.toInt()?.takeIf {
            it <= MAX_PORT_NUMBER
        } ?: configurationManager.torDefaultSocksPort

    override fun getTorConfigurationDir() = configurationManager.torConfigurationDir

    override fun getTorConfPath() = configurationManager.torConfPath

    override fun getTorGeoipPath() = configurationManager.torGeoipPath

    override fun getTorGeoip6Path() = configurationManager.torGeoip6Path

    override fun getTorPidPath() = configurationManager.torPidPath

    override fun getTorDefaultSocksPort() = configurationManager.torDefaultSocksPort

    override fun getTorAssetStream() = configurationManager.torAssetsStream

    override fun getTorConfiguration(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val torConf = fileManager.readFile(configurationManager.torConfPath)
        for (line in torConf) {
            val spaceIndex = line.indexOf(" ")
            if (spaceIndex > 0) {
                val key = line.substring(0, spaceIndex).trim()
                val value = line.substring(spaceIndex, line.length).trim()
                result.add(key to value)
            } else {
                result.add(line to "")
            }
        }
        return result
    }

    override fun updateTorConfiguration(
        originalTorConf: List<Pair<String, String>>,
        newTorConf: List<Pair<String, String>>
    ) {

        if (newTorConf.isEmpty()) {
            return
        }

        var configurationChanged = false

        if (newTorConf.size != originalTorConf.size) {
            configurationChanged = true
        }

        if (!configurationChanged) {
            for (index in originalTorConf.indices) {
                val originalLine = originalTorConf[index]
                val newLine = newTorConf[index]
                if (newLine.first != originalLine.first || newLine.second != originalLine.second) {
                    configurationChanged = true
                    break
                }
            }
        }

        if (configurationChanged) {
            fileManager.rewriteFile(
                configurationManager.torConfPath,
                newTorConf.map {
                    if (it.second.isNotEmpty()) {
                        "${it.first} ${it.second}"
                    } else {
                        it.first
                    }
                }
            )
            actionSender.sendIntent(ACTION_RESTART_TOR)
        }
    }

    override fun getCurrentBridgeType(): BridgeType {

        var bridgeType = BridgeType.NONE

        val torConf = getTorConfiguration()

        for (confLine in torConf) {
            if (confLine.first == "Bridge") {
                bridgeType = getBridgeTypeFromLine(confLine.second)
                break
            }
        }

        return bridgeType
    }

    override fun getSnowflakeBridgeType(): RendezvousType {
        val torConf = getTorConfiguration()

        var rendezvous = RendezvousType.AMP_CACHE

        for (confLine in torConf) {
            if (confLine.first == "Bridge"
                && getBridgeTypeFromLine(confLine.second) == BridgeType.SNOWFLAKE
            ) {
                rendezvous = if (confLine.second.contains("ampcache=")) {
                    RendezvousType.AMP_CACHE
                } else if (confLine.second.contains("sqsqueue=")) {
                    RendezvousType.AMAZON_SQS
                } else {
                    RendezvousType.CDN77
                }
                break
            }
        }

        return rendezvous
    }

    override fun setSnowflakeBridgeType(type: RendezvousType) = try {
        val torConf = getTorConfiguration()
        val newTorConf = setUseSnowflakeBridges(torConf.toList(), type)
        updateTorConfiguration(torConf, newTorConf)
    } catch (e: Exception) {
        loge("ConfigurationRepository setSnowflakeBridgeType $type", e)
    }

    override fun getReverseProxyPath() = configurationManager.reverseProxyPath

    override fun getReverseProxyPidPath() = configurationManager.reverseProxyPidPath

    override fun getReverseProxyDefaultPort() = configurationManager.reverseProxyDefaultPort

    override fun updateCordovaConfiguration(configuration: JSONObject) =
        configurationManager.updateCordovaConfiguration(configuration)

    override fun getTorConfigurationForCordova(): JSONObject {
        val torConf = getTorConfiguration()

        var torPort = configurationManager.torDefaultSocksPort
        var bridgeType = BridgeType.NONE

        for (confLine in torConf) {
            if (confLine.first == "SOCKSPort") {
                torPort = getTorPortFromLine(confLine.second)
            } else if (confLine.first == "Bridge") {
                bridgeType = getBridgeTypeFromLine(confLine.second)
            }
        }

        val torMode = preferences.getTorMode()

        val json = JSONObject().apply {
            put("torMode", torMode.name)
            put("torPort", torPort)
            put("bridgeType", bridgeType.name)
        }

        return json
    }

    override fun saveTorConfigurationFromCordova(json: JSONObject) = try {
        val torConf = getTorConfiguration()
        var newTorConf = torConf.toList()
        if (json.has("torMode")) {
            setTorMode(json.getString("torMode"))
        }
        if (json.has("torPort")) {
            newTorConf = setTorSocksPort(newTorConf, json.getString("torPort"))
        }
        if (json.has("bridgeType")) {
            //TODO use other bridges than snowflake
            val type = try {
                BridgeType.valueOf(json.getString("bridgeType"))
            } catch (_: IllegalArgumentException) {
                BridgeType.NONE
            }
            if (type == BridgeType.SNOWFLAKE) {
                newTorConf = setUseSnowflakeBridges(newTorConf)
            } else {
                newTorConf = clearUseBridges(newTorConf)
            }
        }
        updateTorConfiguration(torConf, newTorConf)
    } catch (e: Exception) {
        loge("ConfigurationRepository saveTorConfigurationFromCordova", e)
    }

    private fun clearUseBridges(
        torConf: List<Pair<String, String>>
    ): List<Pair<String, String>> = try {
        val newTorConf = mutableListOf<Pair<String, String>>()

        for (i in torConf.indices) {
            val pair = torConf[i]
            if (pair.first == "UseBridges") {
                newTorConf.add(Pair("UseBridges", "0"))
            } else if (pair.first != "ClientTransportPlugin" && pair.first != "Bridge") {
                newTorConf.add(pair)
            }
        }

        newTorConf
    } catch (e: Exception) {
        loge("ConfigurationRepository clearUseBridges", e)
        torConf
    }

    private fun setUseSnowflakeBridges(
        torConf: List<Pair<String, String>>,
        type: RendezvousType = RendezvousType.AMP_CACHE
    ): List<Pair<String, String>> = try {

        val newTorConf = mutableListOf<Pair<String, String>>()

        for (i in torConf.indices) {
            val pair = torConf[i]
            if (pair.first != "UseBridges"
                && pair.first != "ClientTransportPlugin"
                && pair.first != "Bridge"
            ) {
                newTorConf.add(pair)
            }
        }

        val bridges = snowflakeRepository.getBridgeLines(type)
        with(newTorConf) {
            add("UseBridges" to "1")
            add("ClientTransportPlugin" to "snowflake exec ${configurationManager.snowflakePath}")
            bridges.forEach {
                add("Bridge" to it)
            }
        }

        newTorConf
    } catch (e: Exception) {
        loge("ConfigurationRepository setUseSnowflakeBridges", e)
        torConf
    }

    private fun setTorSocksPort(
        torConf: List<Pair<String, String>>,
        port: String
    ): List<Pair<String, String>> = try {

        val newPort = port.takeIf {
            it.matches(numberRegex)
        }?.toInt()?.takeIf {
            it <= MAX_PORT_NUMBER
        } ?: 0

        val newTorConf = torConf.toMutableList()
        if (newPort != 0) {
            for (i in newTorConf.indices) {
                val pair = newTorConf[i]
                if (pair.first == "SOCKSPort") {
                    val currentPort = pair.second.substringAfterLast(":")
                    newTorConf[i] = Pair(
                        "SOCKSPort",
                        pair.second.replace(currentPort, newPort.toString())
                    )
                } else if (pair.first == "#SOCKSPort") {
                    val currentPort = pair.second.substringAfterLast(":")
                    newTorConf[i] = Pair(
                        "#SOCKSPort",
                        pair.second.replace(currentPort, newPort.toString())
                    )
                }
            }
        }

        newTorConf
    } catch (e: Exception) {
        loge("ConfigurationRepository setTorSocksPort", e)
        torConf
    }

    private fun setTorMode(torMode: String) {
        val mode = try {
            TorMode.valueOf(torMode)
        } catch (e: IllegalArgumentException) {
            loge("TorPluginManager setTorMode", e)
            TorMode.UNDEFINED
        }
        preferences.setTorMode(mode)
    }

    private fun getTorPortFromLine(line: String): Int =
        line.substringAfter(":")
            .takeIf {
                it.matches(numberRegex)
            }?.toInt()?.takeIf {
                it <= MAX_PORT_NUMBER
            } ?: configurationManager.torDefaultSocksPort

    private fun getBridgeTypeFromLine(line: String): BridgeType =
        when (line.substringBefore(" ")) {
            "obfs3" -> BridgeType.OBFS3
            "obfs4" -> BridgeType.OBFS4
            "meek_lite" -> BridgeType.MEEK_LITE
            "snowflake" -> BridgeType.SNOWFLAKE
            "webtunnel" -> BridgeType.WEBTUNNEL
            else -> BridgeType.VANILLA
        }


}
