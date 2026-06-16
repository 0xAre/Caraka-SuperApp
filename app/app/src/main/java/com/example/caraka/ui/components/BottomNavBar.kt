package com.example.caraka.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.caraka.ui.theme.CarakaTextStyles
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.caraka.R

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    object Home      : Screen("home",        R.string.nav_home,            Icons.Default.Home)
    object Messages  : Screen("messages",    R.string.nav_messages,        Icons.AutoMirrored.Filled.Message)
    object Network   : Screen("network",     R.string.nav_network,         Icons.Default.WifiTethering)
    object Sos       : Screen("sos",         R.string.nav_sos,             Icons.Default.Warning)
    object Settings  : Screen("settings",    R.string.nav_settings,        Icons.Default.Person)
    object Help      : Screen("help",        R.string.help_title,          Icons.Default.Settings)
    object QrIdentity: Screen("qr_identity", R.string.qr_screen_title,    Icons.Default.Settings)
    object Alerts    : Screen("alerts",      R.string.alerts_screen_title, Icons.Default.Warning)
    object Courier   : Screen("courier",     R.string.nav_home,            Icons.Default.Home) // Caraka Kurir

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
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        NavigationBar(
            modifier       = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor   = unselectedColor,
            tonalElevation = 0.dp
        ) {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry.value?.destination?.route

            items.forEach { screen ->
                val isSelected = when (screen) {
                    Screen.Messages -> currentRoute == screen.route || currentRoute?.startsWith("chat/") == true
                    else            -> currentRoute == screen.route
                }
                val title    = stringResource(screen.titleRes)
                val isSos    = screen == Screen.Sos
                val activeColor = if (isSos) MaterialTheme.colorScheme.error
                                  else MaterialTheme.colorScheme.primary

                // Spring-based scale animation for selected item icon
                val iconScale by animateFloatAsState(
                    targetValue   = if (isSelected) 1.12f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMedium
                    ),
                    label = "icon_scale_${screen.route}"
                )

                // Pill width animation
                val pillWidth by animateDpAsState(
                    targetValue   = if (isSelected) 48.dp else 36.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMedium
                    ),
                    label = "pill_width_${screen.route}"
                )

                NavigationBarItem(
                    icon = {
                        val navIcon: @Composable () -> Unit = {
                            Surface(
                                modifier = Modifier.size(width = pillWidth, height = 30.dp),
                                shape    = MaterialTheme.shapes.extraLarge,
                                color    = when {
                                    isSelected && isSos -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                    isSelected          -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else                -> Color.Transparent
                                }
                            ) {
                                Box(
                                    modifier          = Modifier.fillMaxSize(),
                                    contentAlignment  = Alignment.Center
                                ) {
                                    Icon(
                                        screen.icon,
                                        contentDescription = title,
                                        modifier           = Modifier
                                            .size(22.dp)
                                            .scale(iconScale)
                                    )
                                }
                            }
                        }
                        when {
                            screen == Screen.Sos && sosBadgeCount > 0 -> {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor   = Color.White
                                        ) {
                                            Text("$sosBadgeCount", style = CarakaTextStyles.badge)
                                        }
                                    }
                                ) { navIcon() }
                            }
                            screen == Screen.Messages && messagesBadgeCount > 0 -> {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor   = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                if (messagesBadgeCount > 99) "99+" else "$messagesBadgeCount",
                                                style = CarakaTextStyles.badge
                                            )
                                        }
                                    }
                                ) { navIcon() }
                            }
                            else -> navIcon()
                        }
                    },
                    label = {
                        Text(
                            title,
                            style    = if (isSelected) CarakaTextStyles.navLabelSelected else CarakaTextStyles.navLabel,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible
                        )
                    },
                    alwaysShowLabel = true,
                    selected        = isSelected,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = title },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor   = activeColor,
                        unselectedIconColor = unselectedColor,
                        selectedTextColor   = activeColor,
                        unselectedTextColor = unselectedColor,
                        indicatorColor      = Color.Transparent
                    )
                )
            }
        }
    }
}
