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

package pan.alexander.cordova.torrunner.framework

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pan.alexander.cordova.torrunner.App
import pan.alexander.cordova.torrunner.domain.core.CoreState
import pan.alexander.cordova.torrunner.domain.core.CoreStatus
import pan.alexander.cordova.torrunner.domain.core.ReverseProxyManager
import pan.alexander.cordova.torrunner.domain.core.TorManager
import pan.alexander.cordova.torrunner.domain.installer.Installer
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_RELOAD_TOR_CONFIGURATION
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_RESTART_TOR
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_START_TOR
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_SERVICE
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_TOR
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import pan.alexander.cordova.torrunner.utils.network.NetworkChecker
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class CoreService : Service() {

    @Inject
    lateinit var torManager: TorManager
    @Inject
    lateinit var reverseProxyManager: ReverseProxyManager
    @Inject
    lateinit var coreStatus: CoreStatus
    @Inject
    lateinit var installer: Installer
    @Inject
    lateinit var coroutineContext: CoroutineContext

    private val scope by lazy {
        CoroutineScope(coroutineContext + CoroutineName("CoreService"))
    }

    override fun onCreate() {
        App.Companion.instance.daggerComponent.inject(this)
        super.onCreate()

        logi("Core Service started")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {

        if (coreStatus.torState != CoreState.STOPPED) {
            stopTor().also { stopProxy() }
        }

        scope.coroutineContext.cancelChildren()

        logi("Core Service stopped")

        super.onDestroy()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        intent ?: return START_NOT_STICKY
        val action = intent.action ?: return START_NOT_STICKY

        logi("Core service got action: $action")

        when (action) {
            ACTION_START_TOR -> {
                if (NetworkChecker.isNetworkAvailable(this)) {
                    startTor().also { startProxy() }
                } else {
                    logi("But the network is unavailable")
                }
            }
            ACTION_STOP_TOR -> stopTor().also { stopProxy() }
            ACTION_RESTART_TOR -> restartTor()
            ACTION_RELOAD_TOR_CONFIGURATION -> reloadTorConfiguration()
            ACTION_STOP_SERVICE -> stopService()
            else -> {
                stopSelf()
                loge("CoreService unknown action $action")
                return START_NOT_STICKY
            }
        }

        return START_REDELIVER_INTENT
    }

    private fun startTor() = scope.launch {
        waitWhileTorConfigurationInstalling()
        torManager.startTor()
    }

    private fun stopTor() {
        torManager.stopTor()
    }

    private fun restartTor() = scope.launch {
        waitWhileTorConfigurationInstalling()
        torManager.restartTor()
    }

    private fun reloadTorConfiguration() = scope.launch {
        waitWhileTorConfigurationInstalling()
        torManager.reloadTorConfiguration()
    }

    private fun startProxy() {
        reverseProxyManager.startProxy()
    }

    private fun stopProxy() {
        reverseProxyManager.stopProxy()
    }

    private suspend fun waitWhileTorConfigurationInstalling() {
        installer.installTorIfRequired()
        while (isTorConfigurationInstalling()) {
            delay(1.toDuration(DurationUnit.SECONDS))
        }
    }

    private fun isTorConfigurationInstalling() = installer.installing.get()

    private fun stopService() {
        try {
            stopSelf()
        } catch (e: Exception) {
            loge("CoreService unable stop itself", e)
        }
    }

}