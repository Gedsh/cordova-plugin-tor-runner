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
    private val context: Context
) {
    val appDataDir: String by lazy {
        context.applicationInfo.dataDir ?: context.filesDir.path
    }

    val logsDir by lazy { "$appDataDir/logs" }
    val torLogPath by lazy { "$logsDir/Tor.log" }

    val nativeLibPath: String = context.applicationInfo.nativeLibraryDir
    val torPath = "$nativeLibPath/libtor.so"
    val obfsPath = "$nativeLibPath/libobfs4proxy.so"
    val snowflakePath = "$nativeLibPath/libsnowflake.so"
    val webTunnelPath = "$nativeLibPath/libwebtunnel.so"
    val conjurePath = "$nativeLibPath/libconjure.so"

    val torDefaultSocksPort = 9051

    val torConfigurationDir by lazy { "$appDataDir/app_data/tor" }
    val torConfPath by lazy { "$torConfigurationDir/tor.conf" }
    val torCheckerConfPath by lazy { "$torConfigurationDir/tor_checker.conf" }
    val torGeoipPath by lazy { "$torConfigurationDir/geoip" }
    val torGeoip6Path by lazy { "$torConfigurationDir/geoip6" }
    val torPidPath by lazy { "$appDataDir/tor.pid" }
    val torCheckerPidPath by lazy { "$appDataDir/tor_checker.pid" }
    val torCheckerDataDir by lazy { "$appDataDir/tor_checker_data" }

    fun torAssetsStream() = context.assets.open("tor.mp3")

    val torDefaultBridgesPath by lazy { "$appDataDir/app_data/tor/bridges_default.lst" }

    val reverseProxyPath = "$nativeLibPath/libreverseproxy.so"

    val reverseProxyPidPath by lazy { "$appDataDir/reverse-proxy.pid" }

    val reverseProxyDefaultPort = 8181

    fun updateCordovaConfiguration(configuration: JSONObject) {
        instance?.updateConfiguration(configuration)
    }
}
