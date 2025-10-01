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
import pan.alexander.cordova.torrunner.utils.Constants.HOST_NAME_REGEX
import java.io.File
import javax.inject.Inject

private const val SNI_COUNT_TO_CHECK = 5
private const val SNI_COUNT_TO_GET = 3
private const val LOCALE_RU_RU: String = "ru-RU"
private const val LOCALE_RU_BY: String = "ru-BY"
private const val COUNTRY_CODE_RU: String = "ru"

class SniRepositoryImpl @Inject constructor(
    private val configuration: ConfigurationRepository,
    private val addressCheckerRepository: AddressCheckerRepository,
    private val preferences: PreferenceRepository
): SniRepository {

    val hostNameRegex by lazy { Regex(HOST_NAME_REGEX) }

    override fun getFakeSniHosts(): List<String> {
        return getDefaultSni("")
            .shuffled()
            .take(SNI_COUNT_TO_CHECK)
            .let {
                addressCheckerRepository.getReachableDomains(it)
                    .take(SNI_COUNT_TO_GET)
            }.ifEmpty {
                val locales = preferences.getLocales()
                if (locales.contains(LOCALE_RU_RU) || locales.contains(LOCALE_RU_BY)) {
                    getDefaultSni(COUNTRY_CODE_RU)
                        .shuffled()
                        .take(SNI_COUNT_TO_CHECK)
                        .let {
                            addressCheckerRepository.getReachableDomains(it)
                                .take(SNI_COUNT_TO_GET)
                        }.ifEmpty {
                            getDefaultSni(COUNTRY_CODE_RU)
                                .shuffled()
                                .take(SNI_COUNT_TO_GET)
                        }
                } else {
                    getDefaultSni("")
                        .shuffled()
                        .take(SNI_COUNT_TO_GET)
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
        }?.substringAfter(" ")
            ?.split(Regex(", ?"))
            ?.map { it.trim() }
            ?.filter { it.matches(hostNameRegex) }
            ?: emptyList()

    private fun getDefaultBridges(): List<String> =
        File(configuration.getTorDefaultBridgesPath()).readLines()
}
