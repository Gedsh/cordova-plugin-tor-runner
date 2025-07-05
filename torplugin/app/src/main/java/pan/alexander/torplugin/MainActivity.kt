package pan.alexander.torplugin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import pan.alexander.cordova.torrunner.App
import pan.alexander.torplugin.ui.MainScreen
import pan.alexander.torplugin.ui.theme.TorPluginTheme
import javax.inject.Inject

class MainActivity : ComponentActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[MainActivityViewModel::class]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        App.instance.daggerComponent.inject(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TorPluginTheme {
                MainScreen(
                    viewModel.text,
                    viewModel::startTor,
                    viewModel::stopTor,
                    viewModel::restartTor,
                    viewModel::reloadTorConfiguration,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateText()
    }
}
