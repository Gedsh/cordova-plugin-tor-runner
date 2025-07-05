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

package pan.alexander.cordova.torrunner.domain.configuration

import org.json.JSONObject
import java.io.InputStream

interface ConfigurationRepository {
    fun getAppDataDir(): String
    fun getLogsDir(): String

    //Tor
    fun isTorConfigurationAvailable(): Boolean
    fun getTorLogPath(): String
    fun getNativeLibPath(): String
    fun getTorPath(): String
    fun getObfsPath(): String
    fun getSnowflakePath(): String
    fun getWebTunnelPath(): String
    fun getTorSocksPort(): Int
    fun getTorConfigurationDir(): String
    fun getTorConfPath(): String
    fun getTorGeoipPath(): String
    fun getTorGeoip6Path(): String
    fun getTorPidPath(): String
    fun getTorAssetStream(): InputStream

    fun getTorConfiguration(): List<Pair<String, String>>
    fun updateTorConfiguration(
        originalTorConf: List<Pair<String, String>>,
        newTorConf: List<Pair<String, String>>
    )

    //Cordova
    fun updateCordovaConfiguration(configuration: JSONObject)
    fun getTorConfigurationForCordova(): JSONObject
    fun saveTorConfigurationFromCordova(json: JSONObject)
}
