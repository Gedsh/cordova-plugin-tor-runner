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
import org.apache.cordova.CordovaInterface
import org.apache.cordova.PluginResult
import org.json.JSONObject
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.core.CoreState
import pan.alexander.cordova.torrunner.domain.core.CoreStatus
import pan.alexander.cordova.torrunner.domain.installer.Installer
import pan.alexander.cordova.torrunner.framework.ActionSender
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_START_TOR
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_TOR
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import javax.inject.Inject

class TorPluginManager @Inject constructor(
    private val actionSender: ActionSender,
    private val installer: Installer,
    private val configuration: ConfigurationRepository,
    private val coreStatus: CoreStatus
) {

    private var settingsCallback: CallbackContext? = null

    fun startTor(
        cordova: CordovaInterface?,
        callbackContext: CallbackContext?
    ) = runOnBackgroundThread(cordova, callbackContext) {
        if (coreStatus.torState == CoreState.STOPPED) {
            actionSender.sendIntent(ACTION_START_TOR)
        }
        if (coreStatus.torState == CoreState.FAULT) {
            callbackContext?.error("Start Tor failed")
        } else {
            callbackContext?.success()
        }
    }?.let {
        loge("TorManager startTor", it, true)
        throw it
    }

    fun stopTor(
        cordova: CordovaInterface?,
        callbackContext: CallbackContext?
    ) = runOnBackgroundThread(cordova, callbackContext) {
        if (coreStatus.torState == CoreState.RUNNING || coreStatus.torState == CoreState.FAULT) {
            actionSender.sendIntent(ACTION_STOP_TOR)
        }
        callbackContext?.success()
    }?.let {
        loge("TorManager stopTor", it, true)
        throw it
    }

    fun getConfiguration(
        cordova: CordovaInterface?,
        callbackContext: CallbackContext?
    ) = runOnBackgroundThread(cordova, callbackContext) {
        installTorIfRequired()
        val configuration = configuration.getTorConfigurationForCordova()
        updatePluginConfiguration(callbackContext, configuration)
    }?.let {
        loge("TorManager getConfiguration", it, true)
        throw it
    }

    fun setConfiguration(
        cordova: CordovaInterface?,
        options: JSONObject?,
        callbackContext: CallbackContext?
    ) = runOnBackgroundThread(cordova, callbackContext) {
        if (options != null) {
            configuration.saveTorConfigurationFromCordova(options)
            val configuration = configuration.getTorConfigurationForCordova()
            updatePluginConfiguration(configuration)
            callbackContext?.success()
        } else {
            callbackContext?.error("Unable to update undefined configuration")
        }
    }?.let {
        loge("TorManager setConfiguration", it, true)
        throw it
    }

    fun checkAddress(
        cordova: CordovaInterface?,
        address: JSONObject?,
        callbackContext: CallbackContext?
    ) = runOnBackgroundThread(cordova, callbackContext) {
        if (address == null) {
            callbackContext?.error("Unable to check undefined address")
            return@runOnBackgroundThread
        }
        val domain = address.takeIf {
            it.has("address")
        }?.getString("address")
        //TODO
        logi(address.toString())
        val redirect = true
        if (redirect && coreStatus.torState == CoreState.STOPPED) {
            actionSender.sendIntent(ACTION_START_TOR)
        }
        if (coreStatus.torState == CoreState.FAULT) {
            val result = JSONObject().apply {
                put("redirect", false)
                put("port", 0)
            }
            callbackContext?.success(result)
        } else {
            val result = JSONObject().apply {
                put("redirect", redirect)
                put("port", configuration.getTorSocksPort())
            }
            callbackContext?.success(result)
        }
    }?.let {
        loge("TorManager checkAddress", it, true)
        throw it
    }

    private fun updatePluginConfiguration(callback: CallbackContext?, configuration: JSONObject) {
        settingsCallback = callback
        val result = PluginResult(PluginResult.Status.OK, configuration).apply {
            keepCallback = true
        }
        settingsCallback?.sendPluginResult(result)
    }

    fun updatePluginConfiguration(configuration: JSONObject) {
        val result = PluginResult(PluginResult.Status.OK, configuration).apply {
            keepCallback = true
        }
        settingsCallback?.sendPluginResult(result)
    }

    private fun installTorIfRequired(): Boolean =
        installer.installTorIfRequired()

    private fun runOnBackgroundThread(
        cordova: CordovaInterface?,
        callbackContext: CallbackContext?,
        action: () -> Unit
    ): Exception? {
        var exception: Exception? = null
        cordova?.threadPool?.execute {
            try {
                synchronized(this, action)
            } catch (e: Exception) {
                exception = e
                callbackContext?.error(e.toString())
            }
        }
        return exception
    }
}