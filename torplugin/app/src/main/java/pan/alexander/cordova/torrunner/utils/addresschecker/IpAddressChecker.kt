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

package pan.alexander.cordova.torrunner.utils.addresschecker

import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

private const val CONNECT_TIMEOUT_SEC = 5
private const val READ_TIMEOUT_SEC = 5

class IpAddressChecker @Inject constructor() {
    fun isIpAddressReachable(
        ip: String,
        port: Int,
        connectTimeoutSec: Int = CONNECT_TIMEOUT_SEC,
        readTimeoutSec: Int = READ_TIMEOUT_SEC
    ): Boolean = try {
        Socket().use { socket ->
            val address = InetSocketAddress(ip, port)
            socket.connect(address, connectTimeoutSec * 1000)
            socket.soTimeout = readTimeoutSec * 1000
            socket.isConnected
        }
    } catch (_: Exception) {
        false
    }
}
