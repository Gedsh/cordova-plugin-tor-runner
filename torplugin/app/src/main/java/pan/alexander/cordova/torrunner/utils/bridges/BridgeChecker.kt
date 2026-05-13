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

package pan.alexander.cordova.torrunner.utils.bridges

import pan.alexander.cordova.torrunner.utils.Constants.HOST_NAME_REGEX
import pan.alexander.cordova.torrunner.utils.Constants.IPv4_REGEX
import pan.alexander.cordova.torrunner.utils.Constants.IPv6_REGEX
import pan.alexander.cordova.torrunner.utils.Constants.URL_REGEX
import java.util.regex.Pattern
import javax.inject.Inject

private const val ipv4BridgeBase = "(\\d{1,3}\\.){3}\\d{1,3}:\\d+( +\\w{40})?"
private const val ipv6BridgeBase = "\\[$IPv6_REGEX]:\\d+( +\\w{40})?"

class BridgeChecker @Inject constructor() {

    private val urlRegex by lazy { Regex("url=$URL_REGEX") }
    private val frontsRegex by lazy { Regex("fronts=($HOST_NAME_REGEX,)*$HOST_NAME_REGEX") }
    private val frontRegex by lazy { Regex("front=$HOST_NAME_REGEX") }
    private val ampCacheRegex by lazy { Regex("ampcache=$URL_REGEX") }
    private val conjureTransportRegex by lazy { Regex("transport=(min|prefix|dtls)") }
    private val conjureRegistrarRegex by lazy { Regex("registrar=(dns|ampcache)") }
    private val webTunnelServerNameRegex by lazy { Regex("servername=$HOST_NAME_REGEX") }
    private val webTunnelAddrRegex by lazy { Regex("addr=($IPv4_REGEX:\\d+)|$IPv6_REGEX:\\d+") }
    private val webTunnelVersionRegex by lazy { Regex("ver=[0-9.]+") }
    private val snowflakeIceRegex by lazy { Regex("ice=(stun:$HOST_NAME_REGEX:\\d+,?)+") }
    private val snowflakeCovertDtlsRegex by lazy { Regex("covertdtls-config=\\w+") }
    private val snowflakeSqsQueueRegex by lazy { Regex("sqsqueue=$URL_REGEX") }
    private val snowflakeSqsCredsRegex by lazy { Regex("sqscreds=[-A-Za-z0-9+/=]+") }
    private val snowflakeFingerprintRegex by lazy { Regex("fingerprint=\\w+") }


    fun getObfs4BridgeChecker(input: String): (bridge: String) -> Boolean {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^obfs4 +$bridgeBase +cert=.+ +iat-mode=\\d")
        return { bridge -> pattern.matcher(bridge).matches() }
    }

    fun getObfs3BridgeChecker(input: String): (bridge: String) -> Boolean {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^obfs3 +$bridgeBase")
        return { bridge -> pattern.matcher(bridge).matches() }
    }

    fun getScrambleSuitBridgeChecker(input: String): (bridge: String) -> Boolean {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^scramblesuit +$bridgeBase( +password=\\w+)?")
        return { bridge -> pattern.matcher(bridge).matches() }
    }

    fun getMeekLiteBridgeChecker(input: String): (bridge: String) -> Boolean {
        val bridgeBase = input.getBridgeBase()
        val pattern =
            Pattern.compile("^meek_lite +$bridgeBase +url=https://[\\w.+/-]+ +front=[\\w./-]+( +utls=\\w+)?")
        return { bridge -> pattern.matcher(bridge).matches() }
    }

    fun getSnowFlakeBridgeChecker(input: String): (bridge: String) -> Boolean {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^snowflake +$bridgeBase(?: +.+)?")
        return { bridge ->
            pattern.matcher(bridge).matches()
                    && (!bridge.contains("fingerprint=") || bridge.contains(snowflakeFingerprintRegex))
                    && (!bridge.contains("url=") || bridge.contains(urlRegex))
                    && (!bridge.contains("ampcache=") || bridge.contains(ampCacheRegex))
                    && (!bridge.contains("fronts=") || bridge.contains(frontsRegex))
                    && (!bridge.contains("ice=") || bridge.contains(snowflakeIceRegex))
                    && (!bridge.contains("covertdtls-config=") || bridge.contains(snowflakeCovertDtlsRegex))
                    && (!bridge.contains("sqsqueue=") || bridge.contains(snowflakeSqsQueueRegex))
                    && (!bridge.contains("sqscreds=") || bridge.contains(snowflakeSqsCredsRegex))
        }
    }


    fun getConjureBridgeChecker(input: String): (bridge: String) -> Boolean {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^conjure +$bridgeBase .+")
        return { bridge ->
            pattern.matcher(bridge).matches()
                    && bridge.contains(urlRegex)
                    && (!bridge.contains("front=") || bridge.contains(frontRegex))
                    && (!bridge.contains("fronts=") || bridge.contains(frontsRegex))
                    && (!bridge.contains("transport=") || bridge.contains(conjureTransportRegex))
                    && (!bridge.contains("registrar=") || bridge.contains(conjureRegistrarRegex))
                    && (!bridge.contains("registrar=ampcache") || bridge.contains(ampCacheRegex))
        }
    }

    fun getWebTunnelBridgeChecker(input: String): (bridge: String) -> Boolean {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^webtunnel +$bridgeBase ")
        return { bridge ->
            val matcher = pattern.matcher(bridge)
            matcher.find()
                    && bridge.contains(urlRegex)
                    && (!bridge.contains("servername=") || bridge.contains(webTunnelServerNameRegex))
                    && (!bridge.contains("addr=") || bridge.contains(webTunnelAddrRegex))
                    && (!bridge.contains("ver=") || bridge.contains(webTunnelVersionRegex))
                    && (
                    !bridge.replace(matcher.group(), "")
                        .split(" ")
                        .any { !it.contains("=") }
                    )
        }
    }

    fun getOtherBridgeChecker(input: String): (bridge: String) -> Boolean {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile(bridgeBase)
        return { bridge -> pattern.matcher(bridge).matches() }
    }

    private fun String.getBridgeBase() = if (isIPv6Bridge()) {
        ipv6BridgeBase
    } else {
        ipv4BridgeBase
    }

    private fun String.isIPv6Bridge() = contains("[") && contains("]")
}
