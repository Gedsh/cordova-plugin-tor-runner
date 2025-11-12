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

import android.text.Html
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pan.alexander.cordova.torrunner.domain.addresschecker.AddressCheckerRepository
import pan.alexander.cordova.torrunner.domain.addresschecker.DomainToPort
import pan.alexander.cordova.torrunner.domain.addresschecker.IpToPort
import pan.alexander.cordova.torrunner.domain.configuration.BridgeType
import pan.alexander.cordova.torrunner.domain.configuration.BridgesCustomRepository
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationUtils.interleave
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationUtils.isObfs4Bridge
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationUtils.isWebTunnelBridge
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationUtils.isVanillaBridge
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationUtils.webTunnelSniRegex
import pan.alexander.cordova.torrunner.domain.configuration.VanillaRelaysRepository
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceRepository
import pan.alexander.cordova.torrunner.domain.sni.SniRepository
import pan.alexander.cordova.torrunner.utils.Constants.IPv4_REGEX
import pan.alexander.cordova.torrunner.utils.Constants.IPv6_REGEX
import pan.alexander.cordova.torrunner.utils.Constants.MAX_PORT_NUMBER
import pan.alexander.cordova.torrunner.utils.Constants.NUMBER_REGEX
import pan.alexander.cordova.torrunner.utils.Constants.TOR_BRIDGES_ADDRESS
import pan.alexander.cordova.torrunner.utils.Constants.URL_REGEX
import pan.alexander.cordova.torrunner.utils.bridges.BridgeChecker
import pan.alexander.cordova.torrunner.utils.file.FileManager
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import pan.alexander.cordova.torrunner.utils.logger.Logger.logw
import pan.alexander.cordova.torrunner.utils.web.HttpsConnectionManager
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.chunked
import kotlin.collections.firstOrNull
import kotlin.random.Random
import kotlin.text.contains

private const val REQUEST_BRIDGES_INTERVAL_HOURS = 24
private const val REQUEST_BRIDGES_RETRY_INTERVAL_HOURS = 1

private const val MIN_DELAY_MSEC = 1000
private const val MAX_DELAY_MSEC = 5000

@Singleton
class BridgesCustomRepositoryImpl @Inject constructor(
    private val httpsConnectionManager: HttpsConnectionManager,
    private val configuration: ConfigurationRepository,
    private val fileManager: FileManager,
    private val bridgeChecker: BridgeChecker,
    private val preferences: PreferenceRepository,
    private val dispatcherIo: CoroutineDispatcher,
    private val addressCheckerRepository: AddressCheckerRepository,
    private val sniRepository: SniRepository,
    private val vanillaRelaysRepository: VanillaRelaysRepository
) : BridgesCustomRepository {

    private val ipv4BridgeBase = "(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w{40}"
    private val ipv6BridgeBase = "\\[$IPv6_REGEX]:\\d+ +\\w{40}"

    private val vanillaBridgePatternIPv4 by lazy { Pattern.compile(ipv4BridgeBase) }
    private val vanillaBridgePatternIPv6 by lazy { Pattern.compile(ipv6BridgeBase) }

    private val obfs4BridgePatternIPv4 by lazy {
        Pattern.compile("obfs4 +$ipv4BridgeBase +cert=.+ +iat-mode=\\d")
    }
    private val obfs4BridgePatternIPv6 by lazy {
        Pattern.compile("obfs4 +$ipv6BridgeBase +cert=.+ +iat-mode=\\d")
    }

    private val webTunnelBridgePatternIPv4 by lazy {
        Pattern.compile("webtunnel +$ipv4BridgeBase +url=http(s)?://[\\w./-]+")
    }
    private val webTunnelBridgePatternIPv6 by lazy {
        Pattern.compile("webtunnel +$ipv6BridgeBase +url=http(s)?://[\\w./-]+")
    }

    private val numberRegex by lazy { Regex(NUMBER_REGEX) }
    private val urlRegex by lazy { Regex(URL_REGEX) }
    private val ipv4Regex by lazy { Regex(IPv4_REGEX) }

    private val scope by lazy {
        CoroutineScope(
            SupervisorJob() +
                    dispatcherIo.limitedParallelism(1) +
                    CoroutineName("BridgesCustomRepository")
        )
    }

    private val requestingTorBridges = AtomicBoolean(false)

    private val checkBridgesQueue by lazy { generateCheckBridgesQueue() }

    private fun generateCheckBridgesQueue(): MutableList<List<String>> {
        val obfs4Bridges = getCustomObfs4BridgesIPv4().shuffled().chunked(2)
        val webTunnelBridges = getCustomWebTunnelBridges().shuffled().chunked(2)
        val vanillaBridges = getCustomVanillaBridgesIPv4().shuffled()
            .map { it.removePrefix("vanilla ") }
            .chunked(2)

        return interleave(webTunnelBridges, vanillaBridges, obfs4Bridges).toMutableList()
    }

    override fun startRequestingBridgesFromTorProjectDb() {
        try {
            val nextTimeForBridgesRequest = preferences.getNextTimeForBridgesRequest()
            if (System.currentTimeMillis() >= nextTimeForBridgesRequest
                && requestingTorBridges.compareAndSet(false, true)
            ) {
                logi("Start requesting bridges from the Tor Project database")
            } else {
                return
            }

            scope.launch {
                try {
                    makeRandomDelay()
                    val webTunnelBridges = requestWebTunnelBridges()

                    makeRandomDelay()
                    val vanillaBridges = requestVanillaBridgesIPv4().map {
                        "vanilla $it"
                    }

                    makeRandomDelay()
                    val obfs4Bridges = requestObfs4BridgesIPv4()

                    makeRandomDelay()
                    val vanillaRelays = vanillaRelaysRepository.requestVanillaRelays(false).map {
                        "vanilla $it"
                    }

                    val bridges = webTunnelBridges + vanillaBridges + vanillaRelays + obfs4Bridges
                    if (bridges.isNotEmpty()) {
                        preferences.setNextTimeForBridgesRequest(System.currentTimeMillis() + REQUEST_BRIDGES_INTERVAL_HOURS * 60 * 60 * 1000)
                        saveTorBridges(bridges)
                        checkBridgesQueue.clear()
                        checkBridgesQueue.addAll(generateCheckBridgesQueue())
                    } else {
                        preferences.setNextTimeForBridgesRequest(System.currentTimeMillis() + REQUEST_BRIDGES_RETRY_INTERVAL_HOURS * 60 * 60 * 1000)
                    }
                } catch (e: Exception) {
                    preferences.setNextTimeForBridgesRequest(System.currentTimeMillis() + REQUEST_BRIDGES_RETRY_INTERVAL_HOURS * 60 * 60 * 1000)
                    loge("BridgesCustomRepository requestingBridgesFromTorProjectDb", e)
                } finally {
                    requestingTorBridges.set(false)
                }
            }

        } catch (_: CancellationException) {
        } catch (e: Exception) {
            loge("BridgesCustomRepository startRequestingBridgesFromTorProjectDb", e)
        }
    }

    private suspend fun requestWebTunnelBridges() = requestTorBridges(
        transport = BridgeType.WEBTUNNEL,
        ipv6 = false,
        useTor = true
    ).also { bridges ->
        bridges.filter { bridge ->
            currentCoroutineContext().ensureActive()
            isWebTunnelBridgeReachable(bridge)
        }
    }

    private fun isWebTunnelBridgeReachable(bridge: String): Boolean {
        val url = bridge.split(" ").find {
            it.startsWith("url=")
        }?.removePrefix("url=")
        return if (url?.matches(urlRegex) == true) {
            val domainWithPort = url.substringAfter("//").substringBefore("/")
            val domain = domainWithPort.substringBefore(":")
            var port = if (domainWithPort.contains(":")) {
                domainWithPort.substringAfter(":")
            } else {
                "443"
            }
            port =
                if (port.matches(numberRegex) && port.length <= 5 && port.toInt() <= MAX_PORT_NUMBER) {
                    port
                } else {
                    "443"
                }
            addressCheckerRepository.isAddressReachable(
                DomainToPort(
                    domain,
                    port.toInt()
                )
            )
        } else {
            false
        }
    }

    private suspend fun requestVanillaBridgesIPv4() = requestTorBridges(
        transport = BridgeType.VANILLA,
        ipv6 = false,
        useTor = true
    ).also { bridges ->
        bridges.filter { bridge ->
            currentCoroutineContext().ensureActive()
            isVanillaBridgeIPv4Reachable(bridge)
        }
    }

    private fun isVanillaBridgeIPv4Reachable(bridge: String): Boolean {
        val ipWithPort = bridge.substringBefore(" ")
        val ip = ipWithPort.substringBefore(":")
        var port = ipWithPort.substringAfter(":")
        port =
            if (port.matches(numberRegex) && port.length <= 5 && port.toInt() <= MAX_PORT_NUMBER) {
                port
            } else {
                ""
            }
        return if (ip.matches(ipv4Regex) && port.isNotEmpty()) {
            addressCheckerRepository.isAddressReachable(IpToPort(ip, port.toInt()), 5)
        } else {
            false
        }
    }

    private suspend fun requestObfs4BridgesIPv4() = requestTorBridges(
        transport = BridgeType.OBFS4,
        ipv6 = false,
        useTor = true
    ).also { bridges ->
        bridges.filter { bridge ->
            currentCoroutineContext().ensureActive()
            isObfs4BridgeIPv4Reachable(bridge)
        }
    }

    private fun isObfs4BridgeIPv4Reachable(bridge: String): Boolean {
        val ipWithPort = bridge.substringAfter("obfs4 ").substringBefore(" ")
        val ip = ipWithPort.substringBefore(":")
        var port = ipWithPort.substringAfter(":")
        port =
            if (port.matches(numberRegex) && port.length <= 5 && port.toInt() <= MAX_PORT_NUMBER) {
                port
            } else {
                ""
            }
        return if (ip.matches(ipv4Regex) && port.isNotEmpty()) {
            addressCheckerRepository.isAddressReachable(IpToPort(ip, port.toInt()), 5)
        } else {
            false
        }
    }

    override fun stopRequestingBridgesFromTorProjectDb() {
        scope.coroutineContext.cancelChildren()
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
                //Pick next bridge from the queue
                var offset = 1
                while (index + offset < queue.size && currentCoroutineContext().isActive) {
                    if (!currentCoroutineContext().isActive) {
                        break
                    }

                    nextBridges = queue[index + offset]

                    if (sniRepository.isWhiteListSuspected()
                        && nextBridges.firstOrNull()?.isWebTunnelBridge() != true
                        && nextBridges.firstOrNull()?.isVanillaBridge() != true
                    ) {
                        offset++
                    }

                    if (index + offset == queue.size) {
                        nextBridges = queue[0]
                        break
                    }

                    nextBridges = queue[index + offset]

                    if (nextBridges.firstOrNull()?.isWebTunnelBridge() == true
                        && !sniRepository.isWhiteListSuspected()
                    ) {
                        nextBridges = nextBridges.filter { isWebTunnelBridgeReachable(it) }
                        if (nextBridges.isEmpty()) {
                            offset++
                        } else {
                            break
                        }
                    } else if (nextBridges.firstOrNull()?.isObfs4Bridge() == true) {
                        nextBridges = nextBridges.filter { isObfs4BridgeIPv4Reachable(it) }
                        if (nextBridges.isEmpty()) {
                            offset++
                        } else {
                            break
                        }
                    } else if (nextBridges.firstOrNull()?.isVanillaBridge() == true) {
                        nextBridges = nextBridges.filter { isVanillaBridgeIPv4Reachable(it) }
                        if (nextBridges.isEmpty()) {
                            offset++
                        } else {
                            break
                        }
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

    private fun getLastCheckedBridges() = preferences.getLastCustomBridges().toList()

    private fun setLastCheckedBridges(bridges: List<String>) =
        preferences.setLastCustomBridges(bridges.toSet())

    private suspend fun requestTorBridges(
        transport: BridgeType,
        ipv6: Boolean,
        useTor: Boolean
    ): List<String> = try {
        val bridges = mutableListOf<String>()
        val type = when (transport) {
            BridgeType.VANILLA -> "vanilla"
            BridgeType.OBFS4 -> "obfs4"
            BridgeType.WEBTUNNEL -> "webtunnel"
            else -> throw IllegalArgumentException("Requesting ${transport.name} bridges is not supported")
        }
        val url = if (ipv6) {
            "${TOR_BRIDGES_ADDRESS}bridges?transport=${type}&ipv6=yes"
        } else {
            "${TOR_BRIDGES_ADDRESS}bridges?transport=$type"
        }
        httpsConnectionManager.post(url, linkedMapOf(), useTor) { inputStream ->
            inputStream.use { bridges.addAll(parseBridges(it, ipv6)) }
        }
        bridges
    } catch (e: Exception) {
        logw("BridgesCustomRepository requestTorBridges", e)
        emptyList()
    }

    private fun saveTorBridges(bridges: List<String>) = try {
        if (bridges.isNotEmpty()) {
            getCustomBridges().filter {
                !bridges.contains(it)
            }.let {
                fileManager.rewriteFile(
                    configuration.getTorCustomBridgesPath(),
                    (it + bridges).sorted()
                )
            }
        } else {
            false
        }
    } catch (e: Exception) {
        logw("BridgesCustomRepository saveTorBridges", e)
        false
    }

    private fun deleteTorBridges(bridges: List<String>) = try {
        getCustomBridges().filter {
            !bridges.contains(it)
        }.let {
            fileManager.rewriteFile(configuration.getTorCustomBridgesPath(), it)
        }
    } catch (e: Exception) {
        logw("BridgesCustomRepository deleteTorBridges", e)
        false
    }

    private fun getCustomBridges(): List<String> =
        File(configuration.getTorCustomBridgesPath()).readLines()

    private fun getCustomObfs4BridgesIPv4(): List<String> = getCustomBridges().filter {
        val bridges = getCustomBridges().filter {
            it.startsWith("obfs4") && !it.isIPv6Bridge()
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getObfs4BridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    private fun getCustomWebTunnelBridges(): List<String> {
        val bridges = getCustomBridges().filter {
            it.startsWith("webtunnel")
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getWebTunnelBridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    private fun getCustomVanillaBridgesIPv4(): List<String> {
        val bridges = getCustomBridges().filter {
            it.startsWith("vanilla") && !it.isIPv6Bridge()
        }.map {
            it.removePrefix("vanilla ")
        }
        if (bridges.isEmpty()) {
            return emptyList()
        }
        val check = bridgeChecker.getOtherBridgeChecker(bridges.first())
        return bridges.filter { check(it) }
    }

    suspend fun parseBridges(inputStream: InputStream, acceptIPv6: Boolean): List<String> {

        val newBridges = arrayListOf<String>()

        inputStream.bufferedReader().use {
            var line = it.readLine()

            while (line != null && currentCoroutineContext().isActive) {

                if (vanillaBridgePatternIPv4.matcher(line).find()
                    || vanillaBridgePatternIPv6.matcher(line).find()
                ) {
                    parseBridge(unescapeHTML(line), acceptIPv6)?.let { bridge ->
                        newBridges.add(bridge)
                    }
                } else if (newBridges.isNotEmpty() && line.contains("</div>")) {
                    break
                }

                line = it.readLine()
            }

            return newBridges
        }
    }

    private fun parseBridge(line: String, acceptIPv6: Boolean): String? =
        if (containsObfs4Bridge(line)) {
            parseObfs4Bridge(line, acceptIPv6)
        } else if (containsWebTunnelBridge(line)) {
            parseWebTunnelBridge(line)
        } else {
            parseVanillaBridge(line, acceptIPv6)
        }


    private fun parseObfs4Bridge(line: String, acceptIPv6: Boolean): String? {
        val matcherIPv4 = obfs4BridgePatternIPv4.matcher(line)
        if (!acceptIPv6 && matcherIPv4.find()) {
            return matcherIPv4.group()
        }

        val matcherIPv6 = obfs4BridgePatternIPv6.matcher(line)
        if (acceptIPv6 && matcherIPv6.find()) {
            return matcherIPv6.group()
        }

        loge("BridgesCustomRepository parseObfs4Bridge failed $line")

        return null
    }

    private fun parseWebTunnelBridge(line: String): String? {
        val matcherIPv4 = webTunnelBridgePatternIPv4.matcher(line)
        if (matcherIPv4.find()) {
            return matcherIPv4.group()
        }

        val matcherIPv6 = webTunnelBridgePatternIPv6.matcher(line)
        if (matcherIPv6.find()) {
            return matcherIPv6.group()
        }

        loge("BridgesCustomRepository parseWebTunnelBridge failed $line")

        return null
    }

    private fun parseVanillaBridge(line: String, acceptIPv6: Boolean): String? {
        val matcherIPv4 = vanillaBridgePatternIPv4.matcher(line)
        if (!acceptIPv6 && matcherIPv4.find()) {
            return matcherIPv4.group()
        }

        val matcherIPv6 = vanillaBridgePatternIPv6.matcher(line)
        if (acceptIPv6 && matcherIPv6.find()) {
            return matcherIPv6.group()
        }

        loge("BridgesCustomRepository parseVanillaBridge failed $line")

        return null
    }

    private fun String.isIPv6Bridge() = contains("[") && contains("]")

    private fun containsObfs4Bridge(line: String): Boolean = line.contains("obfs4")

    private fun containsWebTunnelBridge(line: String): Boolean = line.contains("webtunnel")

    fun unescapeHTML(line: String): String {
        var result = line
        val pattern = Pattern.compile("&#\\d+;")
        val matcher = pattern.matcher(line)
        if (matcher.find()) {
            result = matcher.replaceAll(
                Html.fromHtml(matcher.group(), Html.FROM_HTML_MODE_LEGACY).toString()
            )
        }
        return result
    }

    private suspend fun makeRandomDelay() {
        delay(Random.nextInt(MIN_DELAY_MSEC, MAX_DELAY_MSEC).toLong())
    }

}
