package pan.alexander.cordova.torrunner.domain.addresschecker

interface AddressCheckerRepository {
    fun isAddressReachable(address: DomainToPort): Boolean
    fun getReachableDomains(domains: List<String>): List<String>
}
