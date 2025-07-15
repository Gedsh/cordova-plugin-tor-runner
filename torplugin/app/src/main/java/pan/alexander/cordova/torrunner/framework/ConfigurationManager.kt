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
import org.json.JSONObject
import pan.alexander.cordova.torrunner.plugin.Plugin.Companion.instance
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigurationManager @Inject constructor(
    context: Context
) {
    val appDataDir: String by lazy {
        context.applicationInfo.dataDir ?: context.filesDir.path
    }

    val logsDir by lazy { "$appDataDir/logs" }
    val torLogPath by lazy { "$logsDir/Tor.log" }

    val nativeLibPath: String by lazy { context.applicationInfo.nativeLibraryDir }
    val torPath by lazy { "$nativeLibPath/libtor.so" }
    val obfsPath by lazy { "$nativeLibPath/libobfs4proxy.so" }
    val snowflakePath by lazy { "$nativeLibPath/libsnowflake.so" }
    val webTunnelPath by lazy { "$nativeLibPath/libwebtunnel.so" }

    val torDefaultSocksPort = 9051

    val torConfigurationDir by lazy { "$appDataDir/app_data/tor" }
    val torConfPath by lazy { "$torConfigurationDir/tor.conf" }
    val torGeoipPath by lazy { "$torConfigurationDir/geoip" }
    val torGeoip6Path by lazy { "$torConfigurationDir/geoip6" }
    val torPidPath by lazy { "$appDataDir/tor.pid" }

    val torAssetsStream by lazy { context.assets.open("tor.mp3") }

    val reverseProxyPath by lazy { "$nativeLibPath/libreverseproxy.so" }

    val reverseProxyPidPath by lazy { "$appDataDir/reverse-proxy.pid" }

    val reverseProxyDefaultPort = 8181

    fun updateCordovaConfiguration(configuration: JSONObject) {
        instance?.updateConfiguration(configuration)
    }
}
