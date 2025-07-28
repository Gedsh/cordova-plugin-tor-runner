package pan.alexander.cordova.torrunner.data.addresschecker

import pan.alexander.cordova.torrunner.domain.addresschecker.AddressCheckerRepository
import pan.alexander.cordova.torrunner.domain.addresschecker.DomainToPort
import pan.alexander.cordova.torrunner.domain.addresschecker.TimeToReachable
import pan.alexander.cordova.torrunner.domain.core.CoreState
import pan.alexander.cordova.torrunner.domain.core.CoreStatus
import pan.alexander.cordova.torrunner.domain.core.TorMode
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceRepository
import pan.alexander.cordova.torrunner.framework.ActionSender
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_TOR
import pan.alexander.cordova.torrunner.utils.addresschecker.AddressChecker
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import pan.alexander.cordova.torrunner.utils.network.NetworkChecker
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val REACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES = 3 * 60 * 1000
private const val UNREACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES = 2 * 60 * 1000
private const val TIME_TO_STOP_TOR_MINUTES = 5 * 60 * 1000

class AddressCheckerRepositoryImpl @Inject constructor(
    private val addressChecker: AddressChecker,
    private val preferences: PreferenceRepository,
    private val networkChecker: NetworkChecker,
    private val coreStatus: CoreStatus,
    private val actionSender: ActionSender
) : AddressCheckerRepository {

    private val checkResults = ConcurrentHashMap<DomainToPort, TimeToReachable>()

    @Volatile
    private var timeLastUnreachableAddress = 0L

    override fun isAddressReachable(address: DomainToPort): Boolean {
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
                addressChecker.isHttpsAddressReachable(
                    address.domain,
                    address.port,
                    8000
                )
            } else if (preferences.getTorMode() == TorMode.AUTO) {
                addressChecker.isHttpsAddressReachable(
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
}
