package com.eldercare.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eldercare.ai.ui.screens.home.HomeScreen
import com.eldercare.ai.ui.screens.menu.MenuScanScreen
import com.eldercare.ai.ui.screens.fridge.FridgeScreen
import com.eldercare.ai.ui.screens.fridge.FridgeHistoryScreen
import com.eldercare.ai.ui.screens.fridge.FridgeHistoryDetailScreen
import com.eldercare.ai.ui.screens.voice.VoiceDiaryScreen
import com.eldercare.ai.ui.screens.settings.SettingsScreen
import com.eldercare.ai.ui.screens.settings.PersonalSituationScreen
import com.eldercare.ai.ui.screens.family.FamilyGuardScreen
import com.eldercare.ai.ui.screens.family.ChildHomeScreen
import com.eldercare.ai.ui.screens.role.RoleSelectionScreen
import com.eldercare.ai.ui.screens.auth.LoginScreen
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.ElderCareDatabase
import com.eldercare.ai.auth.UserService
import com.eldercare.ai.rememberElderCareDatabase
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch

@Composable
fun ElderCareNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val db = rememberElderCareDatabase()
    val userService = remember { 
        try {
            android.util.Log.d("ElderCareNavigation", "Creating UserService...")
            UserService(db.userDao(), db.familyRelationDao(), db.familyLinkRequestDao(), settingsManager)
        } catch (e: Exception) {
            android.util.Log.e("ElderCareNavigation", "Failed to create UserService", e)
            null
        }
    }
    
    var isLoggedIn by remember { mutableStateOf(settingsManager.isLoggedIn()) }
    var userRole by remember { mutableStateOf(settingsManager.getUserRole()) }
    
    // 检查登录状态
    LaunchedEffect(Unit) {
        if (userService != null) {
            try {
                android.util.Log.d("ElderCareNavigation", "Checking login status...")
                if (!isLoggedIn) {
                    // 尝试从数据库获取当前用户
                    val currentUser = userService.getCurrentUser()
                    if (currentUser != null) {
                        isLoggedIn = true
                        userRole = currentUser.role
                        android.util.Log.d("ElderCareNavigation", "Found logged in user: ${currentUser.phone}, role: ${currentUser.role}")
                    } else {
                        android.util.Log.d("ElderCareNavigation", "No logged in user found")
                    }
                } else {
                    android.util.Log.d("ElderCareNavigation", "Already logged in, role: $userRole")
                }
            } catch (e: Exception) {
                android.util.Log.e("ElderCareNavigation", "Error checking login status", e)
                // 如果检查失败，默认显示登录页面
                isLoggedIn = false
            }
        } else {
            android.util.Log.w("ElderCareNavigation", "UserService is null, showing login screen")
            isLoggedIn = false
        }
    }
    
    // 根据登录状态和角色决定起始页面
    val startDestination = when {
        !isLoggedIn -> "login"
        userRole == "parent" -> "home"
        userRole == "child" -> "child_home"
        else -> "login"
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 登录/注册页面
        composable("login") {
            if (userService != null) {
                val scope = rememberCoroutineScope()
                LoginScreen(
                    onAuthSuccess = { next ->
                        isLoggedIn = true
                        userRole = settingsManager.getUserRole()
                        scope.launch {
                            val target = when (next) {
                                "parent_onboarding" -> "personal_situation?onboarding=1"
                                else -> {
                                    if (settingsManager.isParentRole()) {
                                        val healthProfile = db.healthProfileDao().getOnce()
                                        val personalSituation = db.personalSituationDao().getOnce()
                                        val hasAnyHealthInfo = healthProfile != null && (
                                            healthProfile.name.isNotBlank() ||
                                                healthProfile.diseases.isNotEmpty() ||
                                                healthProfile.allergies.isNotEmpty() ||
                                                healthProfile.dietRestrictions.isNotEmpty()
                                            )
                                        val hasAnySituation = personalSituation != null && (
                                            personalSituation.city.isNotBlank() ||
                                                personalSituation.tastePreferences.isNotEmpty() ||
                                                personalSituation.chewLevel.isNotBlank() ||
                                                personalSituation.symptoms.isNotEmpty()
                                            )
                                        if (!hasAnyHealthInfo || !hasAnySituation) {
                                            "personal_situation?onboarding=1"
                                        } else {
                                            "home"
                                        }
                                    } else {
                                        "child_home"
                                    }
                                }
                            }
                            navController.navigate(target) {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    },
                    userService = userService
                )
            } else {
                // 数据库未初始化时显示加载界面
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("正在初始化...")
                    }
                }
            }
        }
        
        // 角色选择页面（已废弃，保留用于兼容）
        composable("role_selection") {
            RoleSelectionScreen(
                onRoleSelected = {
                    userRole = settingsManager.getUserRole()
                    // 根据选择的角色导航到对应首页
                    if (settingsManager.isParentRole()) {
                        navController.navigate("home") {
                            popUpTo("role_selection") { inclusive = true }
                        }
                    } else {
                        navController.navigate("child_home") {
                            popUpTo("role_selection") { inclusive = true }
                        }
                    }
                }
            )
        }
        
        // 父母端首页
        composable("home") {
            HomeScreen(
                onNavigateToMenuScan = { navController.navigate("menu_scan") },
                onNavigateToFridge = { navController.navigate("fridge") },
                onNavigateToVoiceDiary = { navController.navigate("voice_diary") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        
        // 子女端首页
        composable("child_home") {
            ChildHomeScreen(
                onNavigateToFamilyGuard = { navController.navigate("family_guard") },
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHistory = { navController.navigate("fridge_history") }
            )
        }

        composable("fridge_history") {
            FridgeHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { scanId ->
                    navController.navigate("fridge_history/$scanId")
                }
            )
        }

        composable(
            route = "fridge_history/{scanId}",
            arguments = listOf(navArgument("scanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getLong("scanId") ?: 0L
            FridgeHistoryDetailScreen(
                scanId = scanId,
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFamilyGuard = { navController.navigate("family_guard") },
                onNavigateToPersonalSituation = { navController.navigate("personal_situation?onboarding=0") },
                onLogout = {
                    isLoggedIn = false
                    userRole = ""
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable("family_guard") {
            FamilyGuardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "personal_situation?onboarding={onboarding}",
            arguments = listOf(navArgument("onboarding") { type = NavType.IntType; defaultValue = 0 })
        ) { backStackEntry ->
            val onboarding = (backStackEntry.arguments?.getInt("onboarding") ?: 0) == 1
            PersonalSituationScreen(
                onNavigateBack = { navController.popBackStack() },
                onboarding = onboarding
            )
        }
    }
}
