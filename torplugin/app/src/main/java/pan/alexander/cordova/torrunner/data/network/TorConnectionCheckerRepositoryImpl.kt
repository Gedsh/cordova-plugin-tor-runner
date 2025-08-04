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

package pan.alexander.cordova.torrunner.data.network

import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.network.TorConnectionCheckerRepository
import pan.alexander.cordova.torrunner.utils.Constants.CLOUDFLARE_WEBSITE
import pan.alexander.cordova.torrunner.utils.Constants.GOOGLE_WEBSITE
import pan.alexander.cordova.torrunner.utils.Constants.QUAD9_WEBSITE
import pan.alexander.cordova.torrunner.utils.Constants.TOR_PROJECT_WEBSITE
import pan.alexander.cordova.torrunner.utils.addresschecker.AddressChecker
import javax.inject.Inject

class TorConnectionCheckerRepositoryImpl @Inject constructor(
    private val addressChecker: AddressChecker,
    private val configurationRepository: ConfigurationRepository
) : TorConnectionCheckerRepository {

    override fun isTorConnected(): Boolean {
        val domain = listOf(
            TOR_PROJECT_WEBSITE,
            GOOGLE_WEBSITE,
            CLOUDFLARE_WEBSITE,
            QUAD9_WEBSITE
        ).shuffled().first()

        return addressChecker.isHttpsAddressReachable(
            domain = domain,
            port = 443,
            socksPort = configurationRepository.getTorSocksPort()
        )
    }
}
