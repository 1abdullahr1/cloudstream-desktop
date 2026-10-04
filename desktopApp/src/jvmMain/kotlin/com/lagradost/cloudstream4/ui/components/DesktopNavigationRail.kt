package com.lagradost.cloudstream4.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.navigation.Screen

@Composable
fun DesktopNavigationRail(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 20.dp, horizontal = 12.dp)
        ) {
            // App Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.PlayCircleFilled,
                        contentDescription = "CloudStream",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "CloudStream",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Desktop 4.0",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Navigation Items
            NavigationRailItem(
                label = "Home",
                icon = Icons.Default.Home,
                selected = currentScreen is Screen.Home,
                onClick = { onNavigate(Screen.Home) }
            )

            NavigationRailItem(
                label = "Search",
                icon = Icons.Default.Search,
                selected = currentScreen is Screen.Search,
                onClick = { onNavigate(Screen.Search()) }
            )

            NavigationRailItem(
                label = "Library",
                icon = AppIcons.VideoLibrary,
                selected = currentScreen is Screen.Library,
                onClick = { onNavigate(Screen.Library) }
            )

            NavigationRailItem(
                label = "Downloads",
                icon = AppIcons.Download,
                selected = currentScreen is Screen.Downloads,
                onClick = { onNavigate(Screen.Downloads) }
            )

            NavigationRailItem(
                label = "Extensions",
                icon = AppIcons.Extension,
                selected = currentScreen is Screen.Extensions,
                onClick = { onNavigate(Screen.Extensions) }
            )

            Spacer(modifier = Modifier.weight(1f))

            NavigationRailItem(
                label = "Settings",
                icon = Icons.Default.Settings,
                selected = currentScreen is Screen.Settings,
                onClick = { onNavigate(Screen.Settings) }
            )
        }
    }
}

@Composable
private fun NavigationRailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
