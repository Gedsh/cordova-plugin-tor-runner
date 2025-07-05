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

import android.content.Context
import android.content.Intent
import android.os.Build
import pan.alexander.cordova.torrunner.App
import pan.alexander.cordova.torrunner.framework.CoreService
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import javax.inject.Inject

class ActionSender @Inject constructor(
    private val context: Context
) {
    fun sendIntent(action: String) {
        try {

            val intent = Intent(context, CoreService::class.java)
            intent.action = action

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (App.Companion.instance.isAppForeground) {
                    try {
                        context.startService(intent)
                    } catch (e: Exception) {
                        loge("ActionSender sendIntent with action $action", e)
                    }
                }
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            loge("ActionSender sendIntent", e, true)
        }
    }
}
