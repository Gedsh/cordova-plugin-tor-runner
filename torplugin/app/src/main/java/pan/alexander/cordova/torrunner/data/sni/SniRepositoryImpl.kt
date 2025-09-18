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

package pan.alexander.cordova.torrunner.data.sni

import pan.alexander.cordova.torrunner.domain.addresschecker.AddressCheckerRepository
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceRepository
import pan.alexander.cordova.torrunner.domain.sni.SniRepository
import java.io.File
import javax.inject.Inject

private const val SNI_COUNT = 3
private const val LOCALE_RU_RU: String = "ru-RU"
private const val LOCALE_RU_BY: String = "ru-BY"
private const val COUNTRY_CODE_RU: String = "ru"

class SniRepositoryImpl @Inject constructor(
    private val configuration: ConfigurationRepository,
    private val addressCheckerRepository: AddressCheckerRepository,
    private val preferences: PreferenceRepository
): SniRepository {

    override fun getFakeSniHosts(): List<String> {
        return getDefaultSni("")
            .shuffled()
            .take(SNI_COUNT)
            .let {
                addressCheckerRepository.getReachableDomains(it)
            }.ifEmpty {
                val locales = preferences.getLocales()
                if (locales.contains(LOCALE_RU_RU) || locales.contains(LOCALE_RU_BY)) {
                    getDefaultSni(COUNTRY_CODE_RU)
                        .shuffled()
                        .take(SNI_COUNT)
                        .let {
                            addressCheckerRepository.getReachableDomains(it)
                        }
                } else {
                    emptyList()
                }
            }
    }

    private fun getDefaultSni(countryCode: String): List<String> =
        getDefaultBridges().firstOrNull {
            if (countryCode.isEmpty()) {
                it.startsWith("sniall")
            } else {
                it.startsWith("sni$countryCode")
            }
        }?.substringAfter(" ")?.split(", ") ?: emptyList()

    private fun getDefaultBridges(): List<String> =
        File(configuration.getTorDefaultBridgesPath()).readLines()
}
