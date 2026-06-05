package compose.project.click.click.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.calendar.AvailabilityOverlapGap
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

@Composable
fun CalendarOverlapBentoCard(
    dayLabel: String,
    timeLabel: String,
    lockInProgress: Boolean,
    onLockIntent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (lockInProgress) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "calendar_overlap_bento",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner),
        color = GlassSheetTokens.GlassSurface,
        border = BorderStroke(1.dp, GlassSheetTokens.GlassBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "You both have a gap on $dayLabel at $timeLabel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassSheetTokens.OnOled,
                )
            }
            Button(
                onClick = onLockIntent,
                enabled = !lockInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (lockInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Lock Intent")
                }
            }
        }
    }
}

fun AvailabilityOverlapGap.formatDayAndTimeLabels(): Pair<String, String> {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(startEpochMs)
    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
    val local = instant.toLocalDateTime(tz)
    val today = Clock.System.todayIn(tz)
    val dayLabel = when {
        local.date == today -> "Today"
        local.date == today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        else -> local.date.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }
    }
    fun pad(n: Int) = n.toString().padStart(2, '0')
    val hour = local.hour
    val minute = local.minute
    val amPm = if (hour >= 12) "PM" else "AM"
    val hour12 = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    val time = "$hour12:${pad(minute)} $amPm"
    return dayLabel to time
}
