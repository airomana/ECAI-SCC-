package com.eldercare.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.eldercare.ai.ui.screens.home.HomeScreen
import com.eldercare.ai.ui.screens.menu.MenuScanScreen
import com.eldercare.ai.ui.screens.fridge.FridgeScreen
import com.eldercare.ai.ui.screens.voice.VoiceDiaryScreen
import com.eldercare.ai.ui.screens.settings.SettingsScreen

@Composable
fun ElderCareNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToMenuScan = { navController.navigate("menu_scan") },
                onNavigateToFridge = { navController.navigate("fridge") },
                onNavigateToVoiceDiary = { navController.navigate("voice_diary") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        
        composable("menu_scan") {
            MenuScanScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("fridge") {
            FridgeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("voice_diary") {
            VoiceDiaryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}