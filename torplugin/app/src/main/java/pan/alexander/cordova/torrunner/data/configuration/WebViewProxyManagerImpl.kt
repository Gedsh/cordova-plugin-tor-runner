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

    Copyright 2025-2026 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.cordova.torrunner.data.configuration

import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.configuration.WebViewProxyManager
import pan.alexander.cordova.torrunner.framework.WebViewProxy
import pan.alexander.cordova.torrunner.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.cordova.torrunner.utils.logger.Logger.logw
import javax.inject.Inject

class WebViewProxyManagerImpl @Inject constructor(
    private val webViewProxy: WebViewProxy,
    private val configuration: ConfigurationRepository
): WebViewProxyManager {

    override fun activateProxy() = if (webViewProxy.isProxyOverrideSupported()) {
        webViewProxy.activateProxy(LOOPBACK_ADDRESS, configuration.getTorSocksPort())
    } else {
        logw("WebViewProxy is not supported")
    }

    override fun clearProxy() {
        if (webViewProxy.isProxyOverrideSupported()) {
            webViewProxy.clearProxy()
        }
    }
}
