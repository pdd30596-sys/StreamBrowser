package com.duclab.streambrowser

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.duclab.streambrowser.ui.StreamBrowserApp
import com.duclab.streambrowser.ui.theme.StreamBrowserTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        consumeIntent(intent)

        setContent {
            StreamBrowserTheme {
                StreamBrowserApp(
                    viewModel = viewModel,
                    onPictureInPicture = { enterVideoPip() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        if (!::viewModel.isInitialized || intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> viewModel.handleExternalText(intent.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> intent.dataString?.let(viewModel::navigate)
        }
    }

    private fun enterVideoPip() {
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }
}
