package pan.alexander.cordova.torrunner.data.addresschecker

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import pan.alexander.cordova.torrunner.domain.addresschecker.AddressCheckerRepository
import pan.alexander.cordova.torrunner.domain.addresschecker.DomainToPort
import pan.alexander.cordova.torrunner.domain.addresschecker.IpToPort
import pan.alexander.cordova.torrunner.domain.addresschecker.TimeToReachable
import pan.alexander.cordova.torrunner.domain.core.CoreState
import pan.alexander.cordova.torrunner.domain.core.CoreStatus
import pan.alexander.cordova.torrunner.domain.core.TorMode
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceRepository
import pan.alexander.cordova.torrunner.framework.ActionSender
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_TOR
import pan.alexander.cordova.torrunner.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.cordova.torrunner.utils.addresschecker.HttpAddressChecker
import pan.alexander.cordova.torrunner.utils.addresschecker.IpAddressChecker
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import pan.alexander.cordova.torrunner.utils.network.NetworkChecker
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val REACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES = 3 * 60 * 1000
private const val UNREACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES = 2 * 60 * 1000
private const val TIME_TO_STOP_TOR_MINUTES = 5 * 60 * 1000
private const val MAX_SIMULTANEOUS_REACHABILITY_TESTS = 10

class AddressCheckerRepositoryImpl @Inject constructor(
    private val httpAddressChecker: HttpAddressChecker,
    private val ipAddressChecker: IpAddressChecker,
    private val preferences: PreferenceRepository,
    private val networkChecker: NetworkChecker,
    private val coreStatus: CoreStatus,
    private val actionSender: ActionSender,
    dispatcherIo: CoroutineDispatcher
) : AddressCheckerRepository {

    private val limitedParallelismDispatcher = dispatcherIo.limitedParallelism(MAX_SIMULTANEOUS_REACHABILITY_TESTS)

    private val checkResults = ConcurrentHashMap<DomainToPort, TimeToReachable>()

    @Volatile
    private var timeLastUnreachableAddress = 0L

    override fun isAddressReachable(address: DomainToPort): Boolean {
        if (address.domain == "localhost" || address.domain == LOOPBACK_ADDRESS) {
            return true
        }
        val previousResult = checkResults[address]
        val currentTime = System.currentTimeMillis()
        var reachable: Boolean
        if (previousResult == null
            || previousResult.reachable && currentTime - previousResult.time > REACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES
            || !previousResult.reachable && currentTime - previousResult.time > UNREACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES
        ) {

            reachable = if (preferences.getTorMode() == TorMode.NEVER) {
                true
            } else if (preferences.getTorMode() == TorMode.AUTO && networkChecker.isVpnActive()) {
                httpAddressChecker.isHttpsAddressReachable(
                    address.domain,
                    address.port,
                    8000
                )
            } else if (preferences.getTorMode() == TorMode.AUTO) {
                httpAddressChecker.isHttpsAddressReachable(
                    address.domain,
                    address.port,
                    3000
                )
            } else {
                false
            }
            checkResults[address] = TimeToReachable(currentTime, reachable)
        } else {
            reachable = previousResult.reachable
        }

        if (reachable
            && preferences.getTorMode() == TorMode.AUTO
            && timeLastUnreachableAddress != 0L
            && currentTime - timeLastUnreachableAddress > TIME_TO_STOP_TOR_MINUTES
        ) {
            val containsFreshUnreachableAddress = checkResults.values.any {
                !it.reachable && (currentTime - it.time < TIME_TO_STOP_TOR_MINUTES)
            }

            if (checkResults.isNotEmpty()
                && !containsFreshUnreachableAddress
                && coreStatus.torState == CoreState.RUNNING
            ) {
                logi("Stop Tor because of long inactivity")
                actionSender.sendIntent(ACTION_STOP_TOR)
            }
        } else if (!reachable && preferences.getTorMode() == TorMode.AUTO) {
            timeLastUnreachableAddress = System.currentTimeMillis()
        }

        return reachable
    }

    override suspend fun getReachableDomains(
        domains: List<String>,
        timeoutSec: Int
    ): List<String> = try {
        val result = mutableListOf<String>()
        coroutineScope {
            val defers = mutableListOf<Deferred<Unit>>()
            for (domain in domains) {
                defers += async(limitedParallelismDispatcher) {
                    val reachable = httpAddressChecker.isHttpsAddressReachable(
                        domain,
                        443,
                        timeoutSec * 1000
                    )
                    if (reachable) {
                        result.add(domain)
                    }
                }

                delay(timeoutSec * 1000L)
            }

            defers.awaitAll()
        }

        result
    } catch (_: Exception) {
        emptyList()
    }

    override fun isAddressReachable(address: IpToPort): Boolean {
        return ipAddressChecker.isIpAddressReachable(address.ip, address.port)
    }

    override suspend fun getReachableIps(
        ips: List<IpToPort>,
        timeoutSec: Int
    ): List<IpToPort> = try {
        val result = mutableListOf<IpToPort>()
        coroutineScope {
            val defers = mutableListOf<Deferred<Unit>>()
            for (ip in ips) {
                defers += async(limitedParallelismDispatcher) {
                    val reachable = ipAddressChecker.isIpAddressReachable(ip.ip, ip.port)
                    if (reachable) {
                        result.add(ip)
                    }
                }

                delay(timeoutSec * 1000L)
            }

            defers.awaitAll()
        }

        result
    } catch (_: Exception) {
        emptyList()
    }
}
