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

package pan.alexander.cordova.torrunner.plugin

import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONObject
import pan.alexander.cordova.torrunner.App
import pan.alexander.cordova.torrunner.domain.AppManager
import pan.alexander.cordova.torrunner.utils.logger.Logger.LOG_TAG
import javax.inject.Inject


class Plugin : CordovaPlugin() {

    @Inject
    lateinit var appManager: AppManager

    @Inject
    lateinit var torPluginManager: TorPluginManager

    init {
        App.Companion.instance.daggerComponent.inject(this)
    }

    override fun execute(
        action: String?,
        args: JSONArray?,
        callbackContext: CallbackContext?
    ): Boolean {
        try {
            when (action) {
                PluginAction.START_TOR.name -> torPluginManager.startTor(
                    cordova,
                    callbackContext
                )

                PluginAction.STOP_TOR.name -> torPluginManager.stopTor(
                    cordova,
                    callbackContext
                )

                PluginAction.GET_CONFIGURATION.name -> torPluginManager.getConfiguration(
                    cordova,
                    callbackContext
                )

                PluginAction.SET_CONFIGURATION.name -> torPluginManager.setConfiguration(
                    cordova,
                    args?.optJSONObject(0),
                    callbackContext
                )

                PluginAction.CHECK_ADDRESS.name -> torPluginManager.checkAddress(
                    cordova,
                    args?.optJSONObject(0),
                    callbackContext
                )

                else -> {
                    callbackContext?.error("Plugin invalid action: $action")
                    return false
                }
            }
        } catch (e: Exception) {
            handleException(callbackContext, e)
            return false
        }
        return true
    }

    override fun pluginInitialize() {
        instance = this
    }

    override fun onResume(multitasking: Boolean) {
        appManager.onActivityResumed()
    }

    override fun onDestroy() {
        instance = null

        appManager.onActivityDestroyed()
    }

    //Update cordova configuration
    fun updateConfiguration(configuration: JSONObject) {
        torPluginManager.updatePluginConfiguration(configuration)
    }

    private fun handleException(callbackContext: CallbackContext?, e: Exception) {
        callbackContext?.let {
            handleExceptionWithContext(e, it)
        } ?: handleExceptionWithoutContext(e)
    }

    private fun handleExceptionWithContext(e: Exception, context: CallbackContext?) {
        context?.error(e.toString())
    }

    private fun handleExceptionWithoutContext(e: Exception) {
        instance?.logErrorToWebView(e.toString())
    }

    private fun logErrorToWebView(msg: String) {
        executeGlobalJavascript("console.error(\"" + LOG_TAG + "[native]: " + escapeDoubleQuotes(msg) + "\")")
    }

    private fun escapeDoubleQuotes(string: String) =
        string.replace("\"", "\\\"").replace("%22", "\\%22")

    private fun executeGlobalJavascript(jsString: String?) {
        cordova?.activity?.runOnUiThread {
            webView?.loadUrl("javascript:$jsString")
        }
    }

    companion object {
        var instance: Plugin? = null
            private set
    }
}
