package pan.alexander.cordova.torrunner.domain.addresschecker

interface AddressCheckerRepository {
    fun isAddressReachable(address: DomainToPort): Boolean
    suspend fun getReachableDomains(domains: List<String>): List<String>
}
