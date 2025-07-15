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

package pan.alexander.cordova.torrunner.domain

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import pan.alexander.cordova.torrunner.framework.ActionSender
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_START_TOR
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_TOR
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class AppManager @Inject constructor(
    val context: CoroutineContext,
    private val actionSender: ActionSender
) {

    private val scopeIo by lazy {
        CoroutineScope(context + CoroutineName("AppManager"))
    }

    fun onActivityResumed() {
        startTor()
    }

    fun onActivityDestroyed() {

    }

    private fun startTor() {
        actionSender.sendIntent(ACTION_START_TOR)
    }

    private fun stopTor() {
        actionSender.sendIntent(ACTION_STOP_TOR)
    }
}
