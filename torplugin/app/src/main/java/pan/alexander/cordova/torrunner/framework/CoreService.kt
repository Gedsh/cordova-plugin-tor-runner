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
import pan.alexander.cordova.torrunner.App
import pan.alexander.cordova.torrunner.domain.core.CoreState
import pan.alexander.cordova.torrunner.domain.core.CoreStatus
import pan.alexander.cordova.torrunner.domain.core.TorManager
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_RELOAD_TOR_CONFIGURATION
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_RESTART_TOR
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_START_TOR
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_SERVICE
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_TOR
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import javax.inject.Inject

class CoreService : Service() {

    @Inject
    lateinit var torManager: TorManager

    @Inject
    lateinit var coreStatus: CoreStatus

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
            stopTor()
        }

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
            ACTION_START_TOR -> startTor()
            ACTION_STOP_TOR -> stopTor()
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

    private fun startTor() {
        torManager.startTor()
    }

    private fun stopTor() {
        torManager.stopTor()
    }

    private fun restartTor() {
        torManager.restartTor()
    }

    private fun reloadTorConfiguration() {
        torManager.reloadTorConfiguration()
    }

    private fun stopService() {
        try {
            stopSelf()
        } catch (e: Exception) {
            loge("CoreService unable stop itself", e)
        }
    }

}