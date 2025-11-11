package pan.alexander.cordova.torrunner.domain.addresschecker

private const val CHECK_ADDRESS_TIMEOUT_SEC = 1

interface AddressCheckerRepository {
    fun isAddressReachable(address: DomainToPort): Boolean
    suspend fun getReachableDomains(
        domains: List<String>,
        timeoutSec: Int = CHECK_ADDRESS_TIMEOUT_SEC
    ): List<String>
    fun isAddressReachable(address: IpToPort, timeoutSec: Int): Boolean
    suspend fun getReachableIps(
        ips: List<IpToPort>,
        timeoutSec: Int = CHECK_ADDRESS_TIMEOUT_SEC
    ): List<IpToPort>
}
