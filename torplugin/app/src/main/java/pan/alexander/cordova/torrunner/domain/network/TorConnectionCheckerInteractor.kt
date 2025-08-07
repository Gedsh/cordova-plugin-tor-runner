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

package pan.alexander.cordova.torrunner.domain.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import pan.alexander.cordova.torrunner.utils.network.NetworkChecker
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

private const val CHECK_INTERVAL_SEC = 10
private const val ADDITIONAL_DELAY_SEC = 30
private const val CHECKING_LOOP_TIMEOUT_MINT = 20
private const val CHECKING_TIMEOUT_SEC = 120

@Singleton
class TorConnectionCheckerInteractor @Inject constructor(
    private val coroutineContext: CoroutineContext,
    private val networkChecker: NetworkChecker,
    private val torConnectionChecker: TorConnectionCheckerRepository
) {

    private val scopeIo by lazy {
        CoroutineScope(coroutineContext + CoroutineName("TorConnectionCheckerInteractor"))
    }

    private val listenersMap =
        ConcurrentHashMap<String, WeakReference<OnTorConnectionCheckedListener>>()

    private val checking by lazy { AtomicBoolean(false) }

    @Volatile
    private var task: Job? = null

    @Synchronized
    fun <T : OnTorConnectionCheckedListener> addListener(listener: T) {
        listenersMap[listener.javaClass.name] = WeakReference(listener)
    }

    @Synchronized
    fun <T : OnTorConnectionCheckedListener> removeListener(listener: T) {

        listenersMap.remove(listener.javaClass.name)
        if (listenersMap.isEmpty()) {
            task?.let {
                if (!it.isCompleted) {
                    it.cancel()
                }
            }
            task = null
        }
    }

    fun checkInternetConnection() {
        if (checking.compareAndSet(false, true)) {
            checkConnection()
        }
    }

    @Synchronized
    private fun checkConnection() {

        if (task?.isCompleted == false) {
            task?.cancel()
        }

        task = scopeIo.launch {
            tryCheckConnection()
        }
    }

    private suspend fun tryCheckConnection() {
        try {
            withTimeout(CHECKING_LOOP_TIMEOUT_MINT * 60_000L) {
                var internetAvailable = false
                while (isActive && !internetAvailable) {

                    if (!networkChecker.isNetworkAvailable(true)) {
                        makeDelay(CHECK_INTERVAL_SEC)
                        continue
                    }

                    val available = try {
                        withTimeout(CHECKING_TIMEOUT_SEC * 1000L) {
                            check()
                        }
                    } catch (e: SocketTimeoutException) {
                        logException(e)
                        false
                    } catch (e: IOException) {
                        logException(e)
                        makeDelay(ADDITIONAL_DELAY_SEC)
                        false
                    } catch (e: Exception) {
                        logException(e)
                        false
                    }

                    ensureActive()

                    logi("Internet is ${if (available) "available" else "not available"}")

                    internetAvailable = available

                    informListeners(available)

                    if (!available) {
                        makeDelay(CHECK_INTERVAL_SEC)
                    }
                }

                checking.getAndSet(false)
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                loge("TorConnectionCheckerInteractor tryCheckConnection", e)
            }
        } finally {
            checking.compareAndSet(true, false)
        }
    }

    private fun informListeners(available: Boolean) {
        val iterator = listenersMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.get()?.onConnectionChecked(available)
        }
    }

    private suspend fun makeDelay(delaySec: Int) {
        try {
            delay(delaySec * 1000L)
        } catch (_: Exception) {
        }
    }

    private fun logException(e: Exception) {
        loge("TorCheckConnectionInteractor checkConnection", e)
    }

    private suspend fun check(): Boolean = coroutineScope {
        logi("Checking connection via Tor")
        torConnectionChecker.isTorConnected()
    }
}
