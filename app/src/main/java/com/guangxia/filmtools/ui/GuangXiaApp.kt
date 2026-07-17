package com.guangxia.filmtools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guangxia.filmtools.ui.screens.FilmScreen
import com.guangxia.filmtools.ui.screens.FlashScreen
import com.guangxia.filmtools.ui.screens.MeterScreen
import com.guangxia.filmtools.ui.theme.Carbon
import com.guangxia.filmtools.ui.theme.FilmAccent
import com.guangxia.filmtools.ui.theme.FlashAccent
import com.guangxia.filmtools.ui.theme.InstrumentShape
import com.guangxia.filmtools.ui.theme.MeterAccent
import com.guangxia.filmtools.ui.theme.Muted
import com.guangxia.filmtools.ui.theme.Panel
import com.guangxia.filmtools.ui.theme.ToolAccent

private data class Destination(val route: String, val label: String, val icon: ImageVector, val accent: Color)
private val destinations = listOf(
    Destination("meter", "测光", Icons.Rounded.LightMode, MeterAccent),
    Destination("flash", "闪光", Icons.Rounded.FlashOn, FlashAccent),
    Destination("film", "胶卷", Icons.Rounded.Camera, FilmAccent),
)

@Composable
fun GuangXiaApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cameras by viewModel.cameras.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Carbon,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        bottomBar = {
            NavigationBar(
                containerColor = Panel.copy(alpha = 0.96f),
                tonalElevation = 0.dp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).clip(InstrumentShape),
            ) {
                destinations.forEach { destination ->
                    val selected = backStack?.destination?.route == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { if (!selected) navController.navigate(destination.route) { launchSingleTop = true; popUpTo("meter") { saveState = true }; restoreState = true } },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = destination.accent,
                            selectedTextColor = destination.accent,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = "meter") {
                composable("meter") {
                    val reading by viewModel.meterReading.collectAsStateWithLifecycle()
                    val cameraError by viewModel.cameraError.collectAsStateWithLifecycle()
                    ToolAccent(MeterAccent) { MeterScreen(viewModel, settings, reading, cameraError) }
                }
                composable("flash") { ToolAccent(FlashAccent) { FlashScreen(settings.distanceUnit, viewModel::setDistanceUnit) } }
                composable("film") { ToolAccent(FilmAccent) { FilmScreen(cameras, viewModel) } }
            }
        }
    }
}
