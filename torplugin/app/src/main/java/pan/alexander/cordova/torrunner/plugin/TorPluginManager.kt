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
import pan.alexander.cordova.torrunner.domain.addresschecker.AddressCheckerRepository
import pan.alexander.cordova.torrunner.domain.addresschecker.DomainToPort
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.core.CoreState
import pan.alexander.cordova.torrunner.domain.core.CoreStatus
import pan.alexander.cordova.torrunner.domain.core.TorMode
import pan.alexander.cordova.torrunner.domain.installer.Installer
import pan.alexander.cordova.torrunner.framework.ActionSender
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_START_TOR
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_TOR
import pan.alexander.cordova.torrunner.utils.Constants.MAX_PORT_NUMBER
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

@Singleton
class TorPluginManager @Inject constructor(
    private val actionSender: ActionSender,
    private val installer: Installer,
    private val configuration: ConfigurationRepository,
    private val coreStatus: CoreStatus,
    private val addressChecker: AddressCheckerRepository
) {

    private val startTorLock by lazy { ReentrantLock() }
    private val stopTorLock by lazy { ReentrantLock() }
    private val torConfigurationLock by lazy { ReentrantLock() }

    private val portRegex by lazy { Regex("\\d{2,5}") }

    private var settingsCallback: CallbackContext? = null

    fun startTor(
        cordova: CordovaInterface?,
        callbackContext: CallbackContext?
    ) = runOnBackgroundThread(cordova, callbackContext) {
        startTor(callbackContext)
    }?.let {
        loge("TorManager startTor", it, true)
        throw it
    }

    private fun startTor(callbackContext: CallbackContext? = null) {
        startTorLock.withLock {
            if (coreStatus.torState == CoreState.STOPPED) {
                actionSender.sendIntent(ACTION_START_TOR)
            }
            if (coreStatus.torState == CoreState.FAULT) {
                callbackContext?.error("Start Tor failed")
            } else {
                callbackContext?.success()
            }
        }
    }

    fun stopTor(
        cordova: CordovaInterface?,
        callbackContext: CallbackContext?
    ) = runOnBackgroundThread(cordova, callbackContext) {
        stopTor(callbackContext)
    }?.let {
        loge("TorManager stopTor", it, true)
        throw it
    }

    private fun stopTor(callbackContext: CallbackContext? = null) {
        stopTorLock.withLock {
            if (coreStatus.torState == CoreState.RUNNING || coreStatus.torState == CoreState.FAULT) {
                actionSender.sendIntent(ACTION_STOP_TOR)
            }
            callbackContext?.success()
        }
    }

    //Called each time the app is started
    fun getConfiguration(
        cordova: CordovaInterface?,
        callbackContext: CallbackContext?
    ) = runOnBackgroundThread(cordova, callbackContext) {
        torConfigurationLock.withLock {
            installTorIfRequired()
            val configuration = configuration.getTorConfigurationForCordova()
            updatePluginConfiguration(callbackContext, configuration)
        }
    }?.let {
        loge("TorManager getConfiguration", it, true)
        throw it
    }

    fun setConfiguration(
        cordova: CordovaInterface?,
        options: JSONObject?,
        callbackContext: CallbackContext?
    ) = runOnBackgroundThread(cordova, callbackContext) {
        torConfigurationLock.withLock {
            if (options != null) {
                configuration.saveTorConfigurationFromCordova(options)
                val configuration = configuration.getTorConfigurationForCordova()
                updatePluginConfiguration(configuration)
                if (options.has("torMode")) {
                    val mode = try {
                        TorMode.valueOf(options.getString("torMode"))
                    } catch (e: IllegalArgumentException) {
                        loge("TorPluginManager setConfiguration", e)
                        TorMode.UNDEFINED
                    }
                    manageTor(mode)
                }
                callbackContext?.success()
            } else {
                callbackContext?.error("Unable to update undefined configuration")
            }
        }
    }?.let {
        loge("TorManager setConfiguration", it, true)
        throw it
    }

    private fun manageTor(mode: TorMode) {
        if (mode == TorMode.ALWAYS && coreStatus.torState == CoreState.STOPPED) {
            startTor()
        } else if (mode == TorMode.NEVER && coreStatus.torState == CoreState.RUNNING) {
            stopTor()
        }
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
        val domainToPort = address.takeIf {
            it.has("address")
        }?.getString("address")
            ?.removePrefix("https://")
            ?.substringBefore("/")
            ?.let {
                val port = it.substringAfter(":", "443")
                    .takeIf { port ->
                        port.matches(portRegex)
                    }?.toLong()
                    ?.takeIf { port -> port <= MAX_PORT_NUMBER }
                    ?.toInt()
                    ?: 443
                val domain = it.substringBefore(":")
                DomainToPort(domain, port)
            }
        val redirect = domainToPort?.let {
            !addressChecker.isAddressReachable(domainToPort)
        } ?: false

        if (redirect && coreStatus.torState == CoreState.STOPPED) {
            startTor()
        }

        if (coreStatus.isTorReady) {
            val result = JSONObject().apply {
                put("redirect", redirect)
                put("port", configuration.getTorSocksPort())
            }
            callbackContext?.success(result)
        } else {
            val result = JSONObject().apply {
                put("redirect", false)
                put("port", 0)
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
        torConfigurationLock.withLock {
            val result = PluginResult(PluginResult.Status.OK, configuration).apply {
                keepCallback = true
            }
            settingsCallback?.sendPluginResult(result)
        }
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
                action()
            } catch (e: Exception) {
                exception = e
                callbackContext?.error(e.toString())
            }
        }
        return exception
    }
}
