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

object ConfigurationUtils {
    val webTunnelSniRegex by lazy { Regex(" servername(s)?=\\S+") }

    fun String.isObfs4Bridge() = startsWith("obfs4")

    fun String.isWebTunnelBridge() = startsWith("webtunnel")

    fun String.isVanillaBridge() = matches(Regex("^(\\d|\\[).+"))

    fun <T> interleave(vararg lists: List<T>): List<T> {
        val result = mutableListOf<T>()
        val maxSize = lists.maxOf { it.size }

        for (i in 0 until maxSize) {
            for (list in lists) {
                if (i < list.size) {
                    result.add(list[i])
                }
            }
        }

        return result
    }
}
