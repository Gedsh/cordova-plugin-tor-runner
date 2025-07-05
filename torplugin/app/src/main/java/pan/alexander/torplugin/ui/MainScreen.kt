package pan.alexander.torplugin.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pan.alexander.torplugin.ui.theme.TorPluginTheme

@Composable
fun MainScreen(
    text: State<String>,
    onStartButtonClicked: () -> Unit,
    onStopButtonClicked: () -> Unit,
    onRestartButtonClicked: () -> Unit,
    onReloadConfigurationButtonClicked: () -> Unit,
    ) {
    val scrollState = rememberScrollState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
            ) {
                Button(
                    onClick = onStartButtonClicked,
                    modifier = Modifier
                        .padding(4.dp)
                ) {
                    Text("Start")
                }
                Button(
                    onClick = onStopButtonClicked,
                    modifier = Modifier
                        .padding(4.dp)
                ) {
                    Text("Stop")
                }
                Button(
                    onClick = onRestartButtonClicked,
                    modifier = Modifier
                        .padding(4.dp)
                ) {
                    Text("Restart")
                }
                Button(
                    onClick = onReloadConfigurationButtonClicked,
                    modifier = Modifier
                        .padding(4.dp)
                ) {
                    Text("Reload config")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val scrollState = rememberScrollState()
            Text(
                text = text.value,
                style = TextStyle(fontSize = 14.sp),
                modifier = Modifier
                    .weight (1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val text = remember { mutableStateOf("Tor is stopped") }
    TorPluginTheme {
        MainScreen(text, {}, {}, {}, {})
    }
}