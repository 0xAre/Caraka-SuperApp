package com.example.caraka.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.caraka.R
import com.example.caraka.ui.theme.AmberAccent
import com.example.caraka.ui.theme.DangerRed
import com.example.caraka.ui.theme.GlassSurface
import com.example.caraka.ui.theme.SurfaceDark
import com.example.caraka.ui.theme.TextSecondary

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    object Messages : Screen("messages", R.string.nav_messages, Icons.AutoMirrored.Filled.Message)
    object Network : Screen("network", R.string.nav_network, Icons.Default.Map)
    object Sos : Screen("sos", R.string.nav_sos, Icons.Default.Warning)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
    object Help : Screen("help", R.string.help_title, Icons.Default.Settings)
    object QrIdentity : Screen("qr_identity", R.string.qr_screen_title, Icons.Default.Settings)
    object Alerts : Screen("alerts", R.string.alerts_screen_title, Icons.Default.Warning)

    companion object {
        fun chatRoute(peerId: String) = "chat/$peerId"
        const val CHAT_ROUTE_PATTERN = "chat/{peerId}"
    }
}

val items = listOf(
    Screen.Home,
    Screen.Messages,
    Screen.Network,
    Screen.Sos,
    Screen.Settings
)

@Composable
fun BottomNavBar(
    navController: NavController,
    sosBadgeCount: Int = 0,
    messagesBadgeCount: Int = 0
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = AmberAccent, spotColor = SurfaceDark),
        shape = RoundedCornerShape(28.dp),
        color = GlassSurface
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = TextSecondary,
            tonalElevation = 0.dp,
            modifier = Modifier.height(68.dp)
        ) {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry.value?.destination?.route

            items.forEach { screen ->
                val isSelected = when (screen) {
                    Screen.Messages -> currentRoute == screen.route || currentRoute?.startsWith("chat/") == true
                    else -> currentRoute == screen.route
                }
                val title = stringResource(screen.titleRes)
                val accent = if (screen == Screen.Sos) DangerRed else AmberAccent
                NavigationBarItem(
                    icon = {
                        when {
                            screen == Screen.Sos && sosBadgeCount > 0 -> {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = DangerRed, contentColor = Color.White) {
                                            Text("$sosBadgeCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                ) {
                                    Icon(screen.icon, contentDescription = title, modifier = Modifier.size(22.dp))
                                }
                            }
                            screen == Screen.Messages && messagesBadgeCount > 0 -> {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = AmberAccent, contentColor = Color.Black) {
                                            Text(
                                                if (messagesBadgeCount > 99) "99+" else "$messagesBadgeCount",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(screen.icon, contentDescription = title, modifier = Modifier.size(22.dp))
                                }
                            }
                            else -> Icon(screen.icon, contentDescription = title, modifier = Modifier.size(22.dp))
                        }
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
                        selectedIconColor = accent,
                        unselectedIconColor = TextSecondary,
                        selectedTextColor = accent,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = accent.copy(alpha = 0.16f)
                    )
                )
            }
        }
    }
}
