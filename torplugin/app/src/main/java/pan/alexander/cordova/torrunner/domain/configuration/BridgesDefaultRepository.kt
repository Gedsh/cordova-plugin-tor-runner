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

interface BridgesDefaultRepository {
    fun getDefaultBridges(): List<String>
    fun getDefaultObfs4Bridges(): List<String>
    fun getDefaultObfs3Bridges(): List<String>
    fun getDefaultMeekLiteBridges(): List<String>
    fun getDefaultSnowflakeBridges(): List<String>
    fun getDefaultConjureBridges(): List<String>
    fun getDefaultWebTunnelBridges(): List<String>
    fun getDefaultVanillaBridges(): List<String>
    fun getNextBridgesFromAutoQueue(): List<String>
    fun getNextBridgesFromCheckingQueue(currentBridges: List<String>): List<String>
    fun getCheckFailedBridges(): List<String>
    fun addCheckFailedBridge(bridgeAddress: String)
    fun clearCheckFailedBridges()
    fun getAutoQueueLength(): Int
    fun getCheckQueueLength(): Int
    fun updateDefaultBridges()
}
