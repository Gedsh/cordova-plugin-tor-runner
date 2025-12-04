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

package pan.alexander.cordova.torrunner.data.preferences

import android.content.SharedPreferences
import pan.alexander.cordova.torrunner.domain.core.TorMode
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceKeys.TOR_MODE
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceRepository
import javax.inject.Inject
import androidx.core.content.edit
import pan.alexander.cordova.torrunner.domain.configuration.BridgeUnreachableData
import pan.alexander.cordova.torrunner.domain.configuration.BridgesCustomRepository
import pan.alexander.cordova.torrunner.domain.network.NetworkType
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceKeys.LAST_CUSTOM_BRIDGES
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceKeys.LAST_DEFAULT_BRIDGES
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceKeys.LAST_NETWORK
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceKeys.LAST_SNI
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceKeys.LOCALES
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceKeys.NEXT_TIME_FOR_BRIDGES_REQUEST
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceKeys.UNREACHABLE_BRIDGES
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge

private const val BRIDGE_UNREACHABLE_COOLDOWN_TIME_HOURS = 24
private const val BRIDGE_UNREACHABLE_TIME_TO_DELETE_DAYS = 30
private const val BRIDGE_UNREACHABLE_COUNT_TO_DELETE = 3

class PreferenceRepositoryImpl @Inject constructor(
    private val preferences: SharedPreferences,
    private val bridgesCustomRepository: dagger.Lazy<BridgesCustomRepository>
) : PreferenceRepository {

    private val firstNumberRegex by lazy { Regex("^\\d.+") }

    override fun getTorMode(): TorMode =
        TorMode.valueOf(
            preferences.getString(
                TOR_MODE,
                TorMode.UNDEFINED.name
            ) ?: TorMode.UNDEFINED.name
        )

    override fun setTorMode(mode: TorMode) =
        preferences.edit {
            putString(TOR_MODE, mode.name)
        }

    override fun getLastNetwork(): NetworkType =
        NetworkType.valueOf(
            preferences.getString(
                LAST_NETWORK,
                NetworkType.UNKNOWN_NETWORK.name
            ) ?: NetworkType.UNKNOWN_NETWORK.name
        )

    override fun setLastNetwork(networkType: NetworkType) {
        preferences.edit {
            putString(LAST_NETWORK, networkType.name)
        }
    }

    override fun getLocales(): List<String> =
        preferences.getString(LOCALES, "")?.split(",") ?: emptyList()

    override fun setLocales(locales: List<String>) {
        preferences.edit {
            putString(LOCALES, locales.joinToString(","))
        }
    }

    override fun getLastSni(): List<String> =
        preferences.getString(LAST_SNI, "")?.split(",") ?: emptyList()

    override fun setLastSni(sni: List<String>) {
        preferences.edit {
            putString(LAST_SNI, sni.joinToString(","))
        }
    }

    override fun getNextTimeForBridgesRequest(): Long =
        preferences.getLong(NEXT_TIME_FOR_BRIDGES_REQUEST, 0)

    override fun setNextTimeForBridgesRequest(time: Long) {
        preferences.edit {
            putLong(NEXT_TIME_FOR_BRIDGES_REQUEST, time)
        }
    }


    override fun getLastDefaultBridges(): Set<String> =
        preferences.getStringSet(LAST_DEFAULT_BRIDGES, emptySet()) ?: emptySet()

    override fun setLastDefaultBridges(bridges: Set<String>) {
        preferences.edit {
            putStringSet(LAST_DEFAULT_BRIDGES, bridges)
        }
    }

    override fun getLastCustomBridges(): Set<String> =
        preferences.getStringSet(LAST_CUSTOM_BRIDGES, emptySet()) ?: emptySet()

    override fun setLastCustomBridges(bridges: Set<String>) {
        preferences.edit {
            putStringSet(LAST_CUSTOM_BRIDGES, bridges)
        }
    }

    override fun addUnreachableBridgeRecord(bridge: String) = try {
        val bridgeIp = extractBridgeIpAddress(bridge)
        val unreachableBridges = (preferences.getStringSet(
            UNREACHABLE_BRIDGES,
            emptySet()
        ) ?: emptySet())
        val bridgeUnreachableData = unreachableBridges.firstOrNull {
            it.startsWith(bridgeIp)
        }?.split(";")
            ?.let {
                BridgeUnreachableData(
                    bridgeIp,
                    it[1].toLong(),
                    it[2].toLong(),
                    it[3].toInt()
                )
            }

        val currentTime = System.currentTimeMillis()

        if (bridgeIp.isNotEmpty() && bridgeUnreachableData != null
            && bridgeUnreachableData.checkCount > BRIDGE_UNREACHABLE_COUNT_TO_DELETE
            && currentTime - bridgeUnreachableData.lastCheckTime > BRIDGE_UNREACHABLE_COOLDOWN_TIME_HOURS * 1000 * 60 * 60
            && currentTime - bridgeUnreachableData.firstCheckTime > BRIDGE_UNREACHABLE_TIME_TO_DELETE_DAYS * 1000 * 60 * 60 * 24
        ) {
            bridgesCustomRepository.get().deleteBridgeByIp(bridgeIp)
            removeUnreachableBridgeRecord(bridge)
        } else if (bridgeIp.isNotEmpty() && bridgeUnreachableData != null) {
            if (currentTime - bridgeUnreachableData.lastCheckTime > BRIDGE_UNREACHABLE_COOLDOWN_TIME_HOURS * 1000 * 60 * 60) {
                val updatedUnreachableBridges = unreachableBridges.filter {
                    !it.startsWith(bridgeIp)
                }.toMutableSet().apply {
                    add("$bridgeIp;${bridgeUnreachableData.firstCheckTime};$currentTime;${bridgeUnreachableData.checkCount + 1}")
                }
                preferences.edit {
                    putStringSet(UNREACHABLE_BRIDGES, updatedUnreachableBridges)
                }
            }
        } else if (bridgeIp.isNotEmpty()) {
            val updatedUnreachableBridges = unreachableBridges.toMutableSet().apply {
                add("$bridgeIp;$currentTime;$currentTime;1")
            }
            preferences.edit {
                putStringSet(UNREACHABLE_BRIDGES, updatedUnreachableBridges)
            }
        }
        true
    } catch (e: Exception) {
        loge("PreferenceRepository addUnreachableBridge", e)
        false
    }

    override fun removeUnreachableBridgeRecord(bridge: String) = try {
        val bridgeIp = extractBridgeIpAddress(bridge)
        if (bridgeIp.isNotEmpty()) {
            val unreachableBridges = preferences.getStringSet(
                UNREACHABLE_BRIDGES,
                emptySet()
            )?.filter {
                !it.startsWith(bridgeIp)
            }?.toSet() ?: emptySet()
            preferences.edit {
                putStringSet(UNREACHABLE_BRIDGES, unreachableBridges)
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        loge("PreferenceRepository removeUnreachableBridge", e)
        false
    }

    private fun extractBridgeIpAddress(bridge: String): String =
        if (bridge.count { it == " "[0] } > 0 && bridge.matches(firstNumberRegex)) {
            bridge.substringBefore(" ")
        } else if (bridge.count { it == " "[0] } > 1) {
            bridge.substring(bridge.indexOf(" "), bridge.indexOf(" ", bridge.indexOf(" ") + 1))
        } else {
            ""
        }

}
