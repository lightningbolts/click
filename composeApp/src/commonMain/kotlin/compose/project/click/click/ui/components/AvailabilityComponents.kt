package compose.project.click.click.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ActivitySuggestions
import compose.project.click.click.data.models.AvailabilityStatus
import compose.project.click.click.data.models.DayOfWeek
import compose.project.click.click.data.models.MutualAvailability
import compose.project.click.click.data.models.UserAvailability
import compose.project.click.click.ui.theme.PrimaryBlue

/**
 * Quick toggle for "I'm free this week" status
 */
@Composable
fun AvailabilityToggle(
    isFreeThisWeek: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        if (isFreeThisWeek) PrimaryBlue.copy(alpha = 0.15f) 
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
    val borderColor by animateColorAsState(
        if (isFreeThisWeek) PrimaryBlue 
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onToggle() },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFreeThisWeek) PrimaryBlue.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFreeThisWeek) Icons.Filled.EventAvailable else Icons.Filled.EventBusy,
                    contentDescription = null,
                    tint = if (isFreeThisWeek) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isFreeThisWeek) "Free this week!" else "Set as available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFreeThisWeek) PrimaryBlue else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (isFreeThisWeek) 
                        "Others can see you're open to hanging out"
                    else 
                        "Let your connections know you're free",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Toggle or loading
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Switch(
                    checked = isFreeThisWeek,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryBlue,
                        checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

/**
 * Card showing a mutual availability match
 */
@Composable
fun MutualAvailabilityCard(
    mutualAvailability: MutualAvailability,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // User info and match status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            mutualAvailability.otherUserName?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        mutualAvailability.otherUserName ?: "Someone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        mutualAvailability.getSuggestedMeetupMessage(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Match badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Match!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Common days and activities
            if (mutualAvailability.commonDays.isNotEmpty() || mutualAvailability.commonActivities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(mutualAvailability.commonDays.take(3)) { day ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                DayOfWeek.fromString(day)?.shortName ?: day,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    items(mutualAvailability.commonActivities.take(2)) { activity ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                activity,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Quick action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { 
                        onSendMessage(ActivitySuggestions.getSuggestedMessage(
                            mutualAvailability.commonActivities.firstOrNull()
                        ))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Filled.Coffee,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Coffee?")
                }
                
                Button(
                    onClick = { 
                        onSendMessage("Hey! I saw we're both free this week. Want to hang out?")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Message")
                }
            }
        }
    }
}

/**
 * Compact availability indicator for chat headers
 */
@Composable
fun AvailabilityIndicator(
    status: AvailabilityStatus,
    modifier: Modifier = Modifier
) {
    val (icon, color, text) = when (status) {
        AvailabilityStatus.FREE_NOW -> Triple(Icons.Filled.Circle, PrimaryBlue, "Free now")
        AvailabilityStatus.FREE_THIS_WEEK -> Triple(Icons.Filled.EventAvailable, PrimaryBlue, "Free this week")
        AvailabilityStatus.BUSY -> Triple(Icons.Filled.EventBusy, MaterialTheme.colorScheme.error, "Busy")
        AvailabilityStatus.NOT_SET -> return // Don't show anything
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * Day selection chips for availability settings
 */
@Composable
fun DaySelectionRow(
    selectedDays: List<String>,
    onDaysChanged: (List<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(DayOfWeek.entries) { day ->
            val isSelected = selectedDays.any { it.equals(day.name, ignoreCase = true) }
            
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newSelection = if (isSelected) {
                        selectedDays.filter { !it.equals(day.name, ignoreCase = true) }
                    } else {
                        selectedDays + day.name.lowercase()
                    }
                    onDaysChanged(newSelection.mapNotNull { DayOfWeek.fromString(it) })
                },
                label = { Text(day.shortName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlue.copy(alpha = 0.2f),
                    selectedLabelColor = PrimaryBlue
                )
            )
        }
    }
}

/**
 * Activity selection chips for availability settings
 */
@Composable
fun ActivitySelectionRow(
    selectedActivities: List<String>,
    onActivitiesChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ActivitySuggestions.activities) { activity ->
            val isSelected = selectedActivities.contains(activity)
            
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newSelection = if (isSelected) {
                        selectedActivities - activity
                    } else {
                        selectedActivities + activity
                    }
                    onActivitiesChanged(newSelection)
                },
                label = { Text(activity) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }
    }
}
