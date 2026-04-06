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

package pan.alexander.cordova.torrunner.framework

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import javax.inject.Inject
import androidx.webkit.WebViewFeature
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton

@Singleton
class WebViewProxy @Inject constructor() {

    @Volatile
    private var isRunning = false

    private val isSetting = AtomicBoolean(false)

    fun isProxyOverrideSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    fun activateProxy(address: String, port: Int) {
        if (!isRunning && isSetting.compareAndSet(false, true)) {
            ProxyConfig.Builder()
                .addProxyRule("socks://${address}:${port}")
                .addBypassRule("localhost")
                .build().let { proxyConfig ->
                    try {
                        ProxyController.getInstance().setProxyOverride(
                            proxyConfig,
                            SynchronousExecutor(),
                            {
                                isRunning = true
                                isSetting.set(false)
                                logi("WebViewProxy is activated")
                            }
                        )
                    } catch (e: UnsupportedOperationException) {
                        isSetting.set(false)
                        loge("WebViewProxy is not supported", e)
                    } catch (e: IllegalArgumentException) {
                        isSetting.set(false)
                        loge("WebViewProxy invalid configuration", e)
                    } catch (e: Exception) {
                        isSetting.set(false)
                        loge("WebViewProxy failure", e)
                    }
                }
        }

    }

    fun clearProxy() {
        if (isRunning && isSetting.compareAndSet(false, true)) {
            try {
                ProxyController.getInstance().clearProxyOverride(
                    SynchronousExecutor(),
                    {
                        isRunning = false
                        isSetting.set(false)
                        logi("WebViewProxy is disabled")
                    }
                )
            } catch (e: UnsupportedOperationException) {
                isSetting.set(false)
                loge("WebViewProxy is not supported", e)
            } catch (e: Exception) {
                isSetting.set(false)
                loge("WebViewProxy failure", e)
            }
        }
    }

    private class SynchronousExecutor: Executor {
        override fun execute(command: Runnable?) {
            command?.run()
        }
    }
}
