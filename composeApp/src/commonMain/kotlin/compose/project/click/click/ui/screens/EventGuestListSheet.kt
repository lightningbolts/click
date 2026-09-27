@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.GuestListEntryDto // pragma: allowlist secret
import compose.project.click.click.data.api.GuestListStatusDto // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import kotlinx.coroutines.launch

/** How a guest shows in the creator's list: truncated email, then @handle. */
internal fun guestEntryLabel(entry: GuestListEntryDto): String =
    entry.emailTruncated?.trim()?.takeIf { it.isNotEmpty() }
        ?: entry.instagramHandle
            ?.trim()
            ?.removePrefix("@")
            ?.takeIf { it.isNotEmpty() }
            ?.let { "@$it" }
        ?: "Guest"

/** Creator-only guest list import and matching (iOS `GuestListView`, spec §58). */
@Composable
internal fun EventGuestListSheet(
    beaconId: String,
    onDismiss: () -> Unit,
    api: ApiClient = remember { ApiClient() },
) {
    var status by remember(beaconId) { mutableStateOf<PageLoad<GuestListStatusDto>>(PageLoad.Loading) }
    var loadEpoch by remember { mutableIntStateOf(0) }
    var pasted by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(beaconId, loadEpoch) {
        status = PageLoad.Loading
        status = api.getBeaconGuestList(beaconId).fold({ PageLoad.Loaded(it) }, { PageLoad.Failed })
    }

    fun run(
        failure: String,
        call: suspend () -> Result<GuestListStatusDto>,
        onSuccess: () -> Unit = {},
    ) {
        working = true
        scope.launch {
            call().fold(
                onSuccess = {
                    status = PageLoad.Loaded(it)
                    error = null
                    onSuccess()
                },
                onFailure = { error = "$failure ${it.message?.take(140).orEmpty()}".trim() },
            )
            working = false
        }
    }

    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        ClickSheetChrome(
            title = "Guest list",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val s = status) {
                    PageLoad.Loading -> CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    PageLoad.Failed ->
                        TextButton(onClick = { loadEpoch++ }) { Text("Couldn't load the guest list. Retry") }
                    is PageLoad.Loaded -> {
                        GuestCountRow("Guests", s.value.uploaded)
                        GuestCountRow("On Click", s.value.matched)
                        if (s.value.uploaded > s.value.matched) GuestCountRow("Not on Click yet", s.value.uploaded - s.value.matched)
                    }
                }
                Text(
                    "Matching uses emails and Instagram handles. Guests never see who else is on the list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text("Import", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ClickOutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text("Paste emails or @handles, one per line (or CSV)") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        val text = pasted
                        run("Import failed.", { api.postBeaconGuestList(beaconId, source = "csv", csvText = text) }) { pasted = "" }
                    },
                    enabled = !working && pasted.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (working) "Importing…" else "Import") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                val entries = (status as? PageLoad.Loaded)?.value?.entries.orEmpty()
                if (entries.isNotEmpty()) {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Guests",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { run("Matching failed.", { api.matchBeaconGuestList(beaconId) }) },
                            enabled = !working,
                        ) { Text("Re-match") }
                    }
                    entries.forEach { entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(guestEntryLabel(entry), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (entry.matched) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "On Click",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
            }
        }
    }
}

@Composable
private fun GuestCountRow(
    label: String,
    value: Int,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("$value", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
