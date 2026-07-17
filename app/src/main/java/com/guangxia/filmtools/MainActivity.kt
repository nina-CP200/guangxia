package com.guangxia.filmtools

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.guangxia.filmtools.ui.GuangXiaApp
import com.guangxia.filmtools.ui.MainViewModel
import com.guangxia.filmtools.ui.MainViewModelFactory
import com.guangxia.filmtools.ui.theme.GuangXiaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as GuangXiaApplication
        MainViewModelFactory(app.cameraRepository, app.settingsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { GuangXiaTheme { GuangXiaApp(viewModel) } }
    }
}
