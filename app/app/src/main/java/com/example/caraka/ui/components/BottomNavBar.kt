package com.example.caraka.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.DangerRed
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.TextPrimary
import com.example.caraka.ui.theme.TextSecondary

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Messages : Screen("messages", "Messages", Icons.Default.Message)
    object Network : Screen("network", "Network", Icons.Default.Map)
    object Sos : Screen("sos", "SOS", Icons.Default.Warning)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val items = listOf(
    Screen.Home,
    Screen.Messages,
    Screen.Network,
    Screen.Sos,
    Screen.Settings
)

@Composable
fun BottomNavBar(navController: NavController) {
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = AmberAccent, spotColor = SurfaceDark),
        shape = RoundedCornerShape(24.dp),
        color = GlassSurface
    ) {
        NavigationBar(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = TextSecondary,
            tonalElevation = 0.dp
        ) {
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry.value?.destination?.route

        items.forEach { screen ->
            val isSelected = currentRoute == screen.route
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = isSelected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (screen == Screen.Sos) DangerRed else AmberAccent,
                    unselectedIconColor = TextSecondary,
                    selectedTextColor = if (screen == Screen.Sos) DangerRed else AmberAccent,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = SurfaceDark // Hide default indicator pill
                )
            )
        }
    }
}
