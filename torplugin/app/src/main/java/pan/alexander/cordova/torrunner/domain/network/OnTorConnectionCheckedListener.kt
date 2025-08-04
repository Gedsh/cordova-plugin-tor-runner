package pan.alexander.cordova.torrunner.domain.network

interface OnTorConnectionCheckedListener {
    fun onConnectionChecked(available: Boolean)
}
