package pan.alexander.cordova.torrunner.data.addresschecker

import pan.alexander.cordova.torrunner.domain.addresschecker.AddressCheckerRepository
import pan.alexander.cordova.torrunner.domain.addresschecker.DomainToPort
import pan.alexander.cordova.torrunner.domain.addresschecker.TimeToReachable
import pan.alexander.cordova.torrunner.utils.addresschecker.AddressChecker
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val REACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES = 10 * 60 * 1000
private const val UNREACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES = 5 * 60 * 1000

class AddressCheckerRepositoryImpl @Inject constructor(
    private val addressChecker: AddressChecker
) : AddressCheckerRepository {

    private val checkResults = ConcurrentHashMap<DomainToPort, TimeToReachable>()

    override fun isAddressReachable(address: DomainToPort): Boolean {
        val previousResult = checkResults[address]
        val currentTime = System.currentTimeMillis()
        var reachable: Boolean
        if (previousResult == null
            || previousResult.reachable && currentTime - previousResult.time > REACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES
            || !previousResult.reachable && currentTime - previousResult.time > UNREACHABLE_ADDRESS_CHECK_INTERVAL_MINUTES
        ) {
            reachable = addressChecker.isHttpsAddressReachable(
                address.domain,
                address.port
            )
            checkResults[address] = TimeToReachable(System.currentTimeMillis(), reachable)
        } else {
            reachable = previousResult.reachable
        }

        return reachable
    }
}
