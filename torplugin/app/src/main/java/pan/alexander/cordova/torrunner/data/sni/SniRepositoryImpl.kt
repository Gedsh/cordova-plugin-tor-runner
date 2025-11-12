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

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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
) : SniRepository {

    private val hostNameRegex by lazy { Regex(HOST_NAME_REGEX) }

    @Volatile
    private var whiteListSuspected = false

    override suspend fun getFakeSniHosts(): List<String> {

        val fakeSni = mutableListOf<String>()

        val locales = preferences.getLocales()

        val universalSni = getDefaultSni("")
        val reachableUniversalSni = universalSni
            .shuffled()
            .take(SNI_COUNT_TO_CHECK)
            .let {
                addressCheckerRepository.getReachableDomains(it)
                    .take(SNI_COUNT_TO_GET)
            }

        if (reachableUniversalSni.isNotEmpty()) {
            whiteListSuspected = false
            fakeSni.addAll(reachableUniversalSni)
        } else if (locales.contains(LOCALE_RU_RU) || locales.contains(LOCALE_RU_BY)) {
            whiteListSuspected = true
            val ruSni = getDefaultSni(COUNTRY_CODE_RU)
                .shuffled()
                .toMutableList()
            while (ruSni.isNotEmpty() && currentCoroutineContext().isActive) {
                val sniToCheck = ruSni.take(SNI_COUNT_TO_CHECK).also {
                    ruSni.removeAll(it)
                }
                val reachableRuSni = addressCheckerRepository.getReachableDomains(sniToCheck)
                    .take(SNI_COUNT_TO_GET)
                if (reachableRuSni.isNotEmpty()) {
                    fakeSni.addAll(reachableRuSni)
                    break
                }
            }
            if (fakeSni.isEmpty()) {
                fakeSni.addAll(getDefaultSni(COUNTRY_CODE_RU).shuffled().take(SNI_COUNT_TO_GET))
            }
        } else {
            whiteListSuspected = false
            fakeSni.addAll(universalSni.shuffled().take(SNI_COUNT_TO_GET))
        }

        return fakeSni
    }

    override fun isWhiteListSuspected(): Boolean = whiteListSuspected

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
