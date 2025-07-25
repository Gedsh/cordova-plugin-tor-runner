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

package pan.alexander.cordova.torrunner.utils.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import java.util.concurrent.atomic.AtomicBoolean

object NetworkChecker {

    private val checking = AtomicBoolean(false)
    @Volatile
    private var networkAvailable = false

    @Suppress("DEPRECATION")
    @JvmStatic
    fun isNetworkAvailable(context: Context): Boolean =
        try {
            networkAvailable = if (checking.compareAndSet(false, true)) {
                val connectivityManager = context.getConnectivityManager()
                var capabilities: NetworkCapabilities? = null
                if (connectivityManager != null) {
                    capabilities = connectivityManager.getNetworkCapabilities(
                        connectivityManager.activeNetwork
                    )
                }

                if (capabilities != null) {
                    hasActiveTransport(capabilities)
                } else if (connectivityManager != null) {
                    connectivityManager.allNetworks.let {
                        for (network in it) {
                            val networkCapabilities =
                                connectivityManager.getNetworkCapabilities(network)
                            if (networkCapabilities != null && hasActiveTransport(networkCapabilities)) {
                                return true
                            }
                        }
                        return connectivityManager.activeNetworkInfo?.isConnected ?: false
                    }

                } else {
                    true
                }
            } else {
                networkAvailable
            }
            networkAvailable
        } catch (e: Exception) {
            loge("NetworkChecker isNetworkAvailable", e)
            networkAvailable = false
            false
        } finally {
            checking.set(false)
        }

    private fun hasActiveTransport(capabilities: NetworkCapabilities): Boolean =
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        && isCellularInternetMayBeAvailable(capabilities)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

    //This is required because the cellular interface may only be available for ims
    private fun isCellularInternetMayBeAvailable(capabilities: NetworkCapabilities): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        return true
    }

    private fun Context.getConnectivityManager(): ConnectivityManager? =
        getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
}
