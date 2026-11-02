@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.places.MyPlaceVisit
import compose.project.click.click.data.places.PlaceCopy

/** Me → Places load state. */
internal sealed class MyPlacesLoad {
    data object Loading : MyPlacesLoad()

    data class Loaded(
        val visits: List<MyPlaceVisit>,
    ) : MyPlacesLoad()

    data class Failed(
        val message: String,
    ) : MyPlacesLoad()
}

/**
 * Me → Places (§6.10): the Places you've checked in at or met people at. Your own history only.
 * Mirrors iOS `Click/Features/Places/MyPlacesView.swift`.
 */
@Composable
internal fun MyPlacesPageContent(
    load: MyPlacesLoad,
    onOpenPlace: (MyPlaceVisit) -> Unit,
    onRetry: () -> Unit,
) {
    when (load) {
        MyPlacesLoad.Loading ->
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp))
            }
        is MyPlacesLoad.Failed ->
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load your Places", style = MaterialTheme.typography.titleMedium)
                Text(load.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        is MyPlacesLoad.Loaded ->
            if (load.visits.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No Places yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Check in at a Click Place and it shows up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    load.visits.forEach { visit ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPlace(visit) }
                                    .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(visit.place.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                PlaceCopy.visitsLine(visit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PlaceCopy.metPeopleHere(visit.encounterCount)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
    }
}
