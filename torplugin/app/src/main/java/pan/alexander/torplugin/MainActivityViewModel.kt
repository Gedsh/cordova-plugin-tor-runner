package pan.alexander.torplugin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pan.alexander.cordova.torrunner.domain.AppManager
import pan.alexander.cordova.torrunner.domain.core.CoreState
import pan.alexander.cordova.torrunner.domain.core.CoreStatus
import pan.alexander.cordova.torrunner.framework.ActionSender
import pan.alexander.cordova.torrunner.framework.ConfigurationManager
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_RELOAD_TOR_CONFIGURATION
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_RESTART_TOR
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_START_TOR
import pan.alexander.cordova.torrunner.framework.CoreServiceActions.ACTION_STOP_TOR
import pan.alexander.cordova.torrunner.utils.file.FileManager
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import javax.inject.Inject

class MainActivityViewModel @Inject constructor(
    private val fileManager: FileManager,
    private val configuration: ConfigurationManager,
    private val coreStatus: CoreStatus,
    private val actionSender: ActionSender,
    private val appManager: AppManager
) : ViewModel() {

    private val _text = mutableStateOf("")
    val text: State<String> = _text

    fun startTor() {
        actionSender.sendIntent(ACTION_START_TOR)
    }

    fun stopTor() {
        actionSender.sendIntent(ACTION_STOP_TOR)
    }

    fun restartTor() {
        actionSender.sendIntent(ACTION_RESTART_TOR)
    }

    fun reloadTorConfiguration() {
        actionSender.sendIntent(ACTION_RELOAD_TOR_CONFIGURATION)
    }

    fun updateText() {
        appManager.onActivityResumed()
        viewModelScope.launch {
            try {
                while (isActive) {
                    fileManager.readFile(configuration.torLogPath).let {
                        _text.value = it.joinToString("\n")
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                loge("MainActivityViewModel updateText", e)
            }
        }
    }

    override fun onCleared() {
        if (coreStatus.torState != CoreState.STOPPED) {
            actionSender.sendIntent(ACTION_STOP_TOR)
        }
        super.onCleared()
    }
}
