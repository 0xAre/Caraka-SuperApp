package com.example.caraka.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caraka.R
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.DangerRed
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.TextSecondary

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    object Messages : Screen("messages", R.string.nav_messages, Icons.AutoMirrored.Filled.Message)
    object Network : Screen("network", R.string.nav_network, Icons.Default.Map)
    object Sos : Screen("sos", R.string.nav_sos, Icons.Default.Warning)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
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
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = AmberAccent, spotColor = SurfaceDark),
        shape = RoundedCornerShape(28.dp),
        color = GlassSurface
    ) {
        NavigationBar(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = TextSecondary,
            tonalElevation = 0.dp,
            modifier = Modifier.height(68.dp)
        ) {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry.value?.destination?.route

            items.forEach { screen ->
                val isSelected = currentRoute == screen.route
                val title = stringResource(screen.titleRes)
                NavigationBarItem(
                    icon = {
                        Icon(
                            screen.icon,
                            contentDescription = title,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            title,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    },
                    alwaysShowLabel = true,
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
                    modifier = Modifier.semantics { contentDescription = title },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (screen == Screen.Sos) DangerRed else AmberAccent,
                        unselectedIconColor = TextSecondary,
                        selectedTextColor = if (screen == Screen.Sos) DangerRed else AmberAccent,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        }
    }
}
