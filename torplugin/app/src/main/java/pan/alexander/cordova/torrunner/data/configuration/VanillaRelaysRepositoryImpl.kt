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

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import org.json.JSONException
import org.json.JSONObject
import pan.alexander.cordova.torrunner.domain.addresschecker.AddressCheckerRepository
import pan.alexander.cordova.torrunner.domain.addresschecker.IpToPort
import pan.alexander.cordova.torrunner.domain.configuration.RelayAddressFingerprint
import pan.alexander.cordova.torrunner.domain.configuration.VanillaRelaysRepository
import pan.alexander.cordova.torrunner.utils.Constants.IPv6_REGEX
import pan.alexander.cordova.torrunner.utils.Constants.MAX_PORT_NUMBER
import pan.alexander.cordova.torrunner.utils.Constants.NUMBER_REGEX
import pan.alexander.cordova.torrunner.utils.Constants.ONIONOO_SITE_ADDRESS
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logw
import pan.alexander.cordova.torrunner.utils.web.HttpsConnectionManager
import java.lang.IllegalArgumentException
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.text.isNotBlank

private const val MAX_CHECK_RELAY_COUNT = 30
private const val MAX_GET_RELAY_COUNT = 3
private const val FINGERPRINT_LENGTH = 40
private val DESIGNATED_TOR_PORTS = listOf("9001", "9030", "9040", "9050", "9051", "9150")

class VanillaRelaysRepositoryImpl @Inject constructor(
    private val httpsConnectionManager: HttpsConnectionManager,
    private val addressCheckerRepository: AddressCheckerRepository
) : VanillaRelaysRepository {

    private val bridgeIPv4Pattern =
        Pattern.compile("([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}):(\\d+)\\b")
    private val bridgeIPv6Pattern =
        Pattern.compile("\\[$IPv6_REGEX]:(\\d+)\\b")

    private val numberRegex by lazy { Regex(NUMBER_REGEX) }

    override suspend fun requestVanillaRelays(allowIPv6Relays: Boolean): List<String> =
        getRelaysWithFingerprintAndAddress(allowIPv6Relays)
            .shuffled()
            .take(MAX_CHECK_RELAY_COUNT)
            .sortedBy { it.port.toInt() }
            .asFlow()
            .filter {
                currentCoroutineContext().isActive
                        && !DESIGNATED_TOR_PORTS.contains(it.port)
                        && addressCheckerRepository.isAddressReachable(
                    IpToPort(it.ip, it.port.toInt()), 2
                )
            }
            .take(MAX_GET_RELAY_COUNT)
            .toList()
            .map {
                "${it.ip}:${it.port} ${it.fingerprint}"
            }

    private suspend fun getRelaysWithFingerprintAndAddress(
        allowIPv6Relays: Boolean
    ): List<RelayAddressFingerprint> {

        val relays = mutableListOf<RelayAddressFingerprint>()

        requestRelaysWithFingerprintAndAddress()
            .forEach {
                try {
                    if (it.contains("fingerprint")) {
                        val relay = mapJsonToRelay(JSONObject(it), allowIPv6Relays)
                        relays.addAll(relay)
                    }
                } catch (e: Exception) {
                    logw("VanillaRelaysRepository getRelaysWithFingerprintAndAddress", e)
                }
            }

        return relays
    }

    private suspend fun requestRelaysWithFingerprintAndAddress(): List<String> = try {
        httpsConnectionManager.get(
            "${ONIONOO_SITE_ADDRESS}details",
            linkedMapOf<String, String>().apply {
                put("type", "relay")
                put("running", "true")
                put("fields", "fingerprint,or_addresses")
            }, true
        )
    } catch (e: Exception) {
        loge("VanillaRelaysRepository requestRelaysWithFingerprintAndAddress", e)
        emptyList()
    }


    private fun mapJsonToRelay(
        json: JSONObject,
        allowIPv6Relays: Boolean
    ): List<RelayAddressFingerprint> {

        val relays = mutableListOf<RelayAddressFingerprint>()

        val relayIPv4Line = try {
            json.getJSONArray("or_addresses").getString(0)
        } catch (_: JSONException) {
            ""
        }
        val relayIPv6Line = try {
            json.getJSONArray("or_addresses").getString(1)
        } catch (_: JSONException) {
            ""
        }
        val fingerprint = json.getString("fingerprint")

        parseIPv4Relay(relayIPv4Line, fingerprint)?.let { relays.add(it) }

        if (allowIPv6Relays && relayIPv6Line.isIPv6Bridge()) {
            parseIPv6Relay(relayIPv6Line, fingerprint)?.let { relays.add(it) }
        }

        if (relays.isNotEmpty()) {
            return relays
        }

        throw IllegalArgumentException("JSON $json is not valid relay")

    }

    private fun parseIPv4Relay(relayLine: String, fingerprint: String): RelayAddressFingerprint? {
        val matcher = bridgeIPv4Pattern.matcher(relayLine)
        return mapToRelayAddressFingerprint(matcher, fingerprint)
    }

    private fun parseIPv6Relay(relayLine: String, fingerprint: String): RelayAddressFingerprint? {
        val matcher = bridgeIPv6Pattern.matcher(relayLine)
        return mapToRelayAddressFingerprint(matcher, fingerprint)
    }

    private fun mapToRelayAddressFingerprint(
        matcher: Matcher,
        fingerprint: String
    ): RelayAddressFingerprint? {
        if (matcher.find()) {
            val ip = matcher.group(1)
            val port = matcher.group(2)

            if (ip != null && ip.isNotBlank()
                && port != null && port.isNotBlank()
                && port.matches(numberRegex) && port.length <= 5 && port.toInt() <= MAX_PORT_NUMBER
                && fingerprint.length == FINGERPRINT_LENGTH
            ) {
                return RelayAddressFingerprint(
                    ip = ip,
                    port = port,
                    fingerprint = fingerprint
                )
            }
        }
        return null
    }

    private fun String.isIPv6Bridge() = contains("[") && contains("]")
}
