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

package pan.alexander.cordova.torrunner.framework

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import pan.alexander.cordova.torrunner.domain.network.NetworkType
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceRepository
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import java.util.concurrent.ConcurrentSkipListSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkObserver @Inject constructor(
    private val context: Context,
    private val preferenceRepository: PreferenceRepository
) {

    @Volatile
    private var lastNetworkType: NetworkType = NetworkType.UNKNOWN_NETWORK
    private val lastCapabilities: MutableSet<Int> = ConcurrentSkipListSet()
    private val lastDns: MutableSet<Int> = ConcurrentSkipListSet()

    private val networkChanged = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    val networkChanges = networkChanged as SharedFlow<Unit>

    fun listenNetworkChanges() = try {
        connectivityManager?.registerNetworkCallback(
            networkRequest,
            networkCallback
        )
        lastNetworkType = preferenceRepository.getLastNetwork()
    } catch (e: Exception) {
        loge("NetworkObserver listenNetworkChanges", e)
    }

    fun unlistenNetworkChanges() = try {
        preferenceRepository.setLastNetwork(lastNetworkType)
        connectivityManager?.unregisterNetworkCallback(networkCallback)
    } catch (e: Exception) {
        loge("NetworkObserver unlistenNetworkChanges", e)
    }

    private val connectivityManager by lazy { context.getConnectivityManager() }

    private val networkRequest by lazy {
        NetworkRequest.Builder()
            .addTransportType(TRANSPORT_WIFI)
            .addTransportType(TRANSPORT_ETHERNET)
            .addTransportType(TRANSPORT_CELLULAR)
            .addTransportType(TRANSPORT_VPN)
            .removeCapability(NET_CAPABILITY_INTERNET)
            .removeCapability(NET_CAPABILITY_NOT_VPN)
            .removeCapability(NET_CAPABILITY_VALIDATED)
            .build()
    }

    private val networkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                networkChanged.tryEmit(Unit)
                logi("Network available ${network.toType()}")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val capabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    networkCapabilities.capabilities.toSet()
                } else {
                    null
                }
                if (network.isActiveNetwork()
                    && capabilities != null
                    && !lastCapabilities.containsAll(capabilities)) {
                    if (lastCapabilities.isNotEmpty()) {
                        networkChanged.tryEmit(Unit)
                    }
                    lastCapabilities.clear()
                    lastCapabilities.addAll(capabilities)
                    logi("Network capabilities changed ${network.toType()} $networkCapabilities")
                }
                saveLastActiveNetwork()
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                val dns = linkProperties.dnsServers.map { it.hashCode() }.toSet()
                if (network.isActiveNetwork()
                    && !lastDns.containsAll(dns)) {
                    if (lastDns.isNotEmpty()) {
                        networkChanged.tryEmit(Unit)
                    }
                    lastDns.clear()
                    lastDns.addAll(dns)
                }
                saveLastActiveNetwork()
                logi("Network link properties changed ${network.toType()} $linkProperties")
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (network.isActiveNetwork()) {
                    networkChanged.tryEmit(Unit)
                }
                saveLastActiveNetwork()
                logi("Network blocked status changed ${network.toType()} blocked $blocked")
            }

            override fun onUnavailable() {
                lastNetworkType = NetworkType.UNKNOWN_NETWORK
                logi("Network unavailable")
            }

            override fun onLost(network: Network) {
                val networkType = network.toType()
                if (networkType == lastNetworkType || networkType == NetworkType.UNKNOWN_NETWORK) {
                    lastNetworkType = NetworkType.UNKNOWN_NETWORK
                }
                logi("Network lost ${network.toType()}")
            }

        }
    }

    fun Network.isActiveNetwork(): Boolean {
        val  manager = connectivityManager
        if (manager == null) {
            return true
        }

        return manager.activeNetwork == this
    }

    private fun Network.toType(): NetworkType {
        val manager = connectivityManager
        if (manager == null) {
            return NetworkType.UNKNOWN_NETWORK
        }
        val capabilities = manager.getNetworkCapabilities(this)
        if (capabilities == null) {
            return NetworkType.UNKNOWN_NETWORK
        }

        return when {
            capabilities.hasTransport(TRANSPORT_ETHERNET) -> NetworkType.ETHERNET_NETWORK
            capabilities.hasTransport(TRANSPORT_WIFI) -> NetworkType.WIFI_NETWORK
            capabilities.hasTransport(TRANSPORT_CELLULAR) -> NetworkType.CELLULAR_NETWORK

            else -> NetworkType.UNKNOWN_NETWORK
        }
    }

    private fun saveLastActiveNetwork() {
        val activeNetwork = connectivityManager?.activeNetwork?.toType() ?: NetworkType.UNKNOWN_NETWORK
        if (lastNetworkType != activeNetwork) {
            networkChanged.tryEmit(Unit)
            lastNetworkType = activeNetwork
        }
    }

    private fun Context.getConnectivityManager(): ConnectivityManager? =
        getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
}
