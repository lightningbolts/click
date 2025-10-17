package ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import theme.ClickColors

enum class NavDestination {
    HOME, CLICK, CONNECTIONS, MAP, SETTINGS
}

@Composable
fun BottomNavigationBar(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Animate the background blur effect
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (searchExpanded) 0.98f else 1f,
        animationSpec = tween(300)
    )

    Box(modifier = modifier.fillMaxWidth()) {
        // Main Navigation Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (searchExpanded) 120.dp else 72.dp),
            color = ClickColors.Surface.copy(alpha = backgroundAlpha),
            elevation = 8.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column {
                // Search bar (when expanded)
                AnimatedVisibility(
                    visible = searchExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = { searchExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                // Navigation Items
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavItem(
                        icon = Icons.Default.Home,
                        label = "Home",
                        selected = currentDestination == NavDestination.HOME,
                        onClick = { onNavigate(NavDestination.HOME) }
                    )

                    NavItem(
                        icon = Icons.Default.Phone,
                        label = "Click",
                        selected = currentDestination == NavDestination.CLICK,
                        onClick = { onNavigate(NavDestination.CLICK) },
                        isPrimary = true
                    )

                    NavItem(
                        icon = Icons.Default.Person,
                        label = "Connect",
                        selected = currentDestination == NavDestination.CONNECTIONS,
                        onClick = { onNavigate(NavDestination.CONNECTIONS) }
                    )

                    NavItem(
                        icon = Icons.Default.Place,
                        label = "Map",
                        selected = currentDestination == NavDestination.MAP,
                        onClick = { onNavigate(NavDestination.MAP) }
                    )

                    // Search Icon (expands into search bar)
                    SearchNavItem(
                        expanded = searchExpanded,
                        onClick = { searchExpanded = !searchExpanded }
                    )

                    NavItem(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        selected = currentDestination == NavDestination.SETTINGS,
                        onClick = { onNavigate(NavDestination.SETTINGS) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isPrimary -> ClickColors.Primary
            selected -> ClickColors.Primary
            else -> ClickColors.TextTertiary
        },
        animationSpec = tween(200)
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (isPrimary) 44.dp else 40.dp)
                .clip(if (isPrimary) CircleShape else RoundedCornerShape(10.dp))
                .background(
                    when {
                        isPrimary -> ClickColors.Primary.copy(alpha = 0.1f)
                        selected -> ClickColors.Primary.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(if (isPrimary) 24.dp else 22.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) ClickColors.TextPrimary else ClickColors.TextTertiary
        )
    }
}

@Composable
private fun SearchNavItem(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (expanded) ClickColors.Primary.copy(alpha = 0.08f)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Search,
                contentDescription = "Search",
                tint = if (expanded) ClickColors.Primary else ClickColors.TextTertiary,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Search",
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            ),
            color = if (expanded) ClickColors.TextPrimary else ClickColors.TextTertiary
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Liquid glass effect
    Surface(
        modifier = modifier.height(48.dp),
        color = ClickColors.SurfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = ClickColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = ClickColors.TextPrimary,
                    fontSize = 16.sp
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search connections, places...",
                            style = TextStyle(
                                color = ClickColors.TextTertiary,
                                fontSize = 16.sp
                            )
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = ClickColors.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
