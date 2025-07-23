package pan.alexander.cordova.torrunner.domain.addresschecker

interface AddressCheckerRepository {
    fun isAddressReachable(address: DomainToPort): Boolean
}
