package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until

private const val MinSignupAgeYears = 13

private fun parseIsoLocalDate(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

private fun formatBirthdayDisplay(date: LocalDate): String {
    val month = date.monthNumber.toString().padStart(2, '0')
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "$month/$day/${date.year}"
}

private fun utcMillisToIsoDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toString().take(10)

private fun isAtLeastAge(birthDate: LocalDate, years: Int): Boolean {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val ageYears = birthDate.until(today, DateTimeUnit.YEAR)
    return ageYears >= years
}

/**
 * Blocking gate for OAuth (and any) accounts missing [public.users] birthday and/or first name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBasicsGateScreen(
    userId: String,
    initialFirstName: String,
    initialLastName: String,
    onCompleted: () -> Unit,
) {
    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName by remember { mutableStateOf(initialLastName) }
    var birthdayIso by remember { mutableStateOf("") }
    var showBirthdayPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repo = remember { SupabaseRepository() }
    val scroll = rememberScrollState()

    val parsedBirth = remember(birthdayIso) { parseIsoLocalDate(birthdayIso) }
    val birthdayDisplay = remember(parsedBirth) { parsedBirth?.let(::formatBirthdayDisplay).orEmpty() }
    val birthdayValid = parsedBirth != null && isAtLeastAge(parsedBirth, MinSignupAgeYears)
    val birthdayHelper = when {
        birthdayIso.isBlank() -> "Required — select your birthday"
        parsedBirth == null -> "Select a valid date"
        !isAtLeastAge(parsedBirth, MinSignupAgeYears) -> "You must be at least $MinSignupAgeYears years old"
        else -> null
    }

    val canSave =
        firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            birthdayValid &&
            !saving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Complete your profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "We need your name and date of birth to continue. This keeps Click safe and age-appropriate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = birthdayDisplay,
            onValueChange = { },
            label = { Text("Birthday") },
            placeholder = { Text("MM/DD/YYYY") },
            supportingText = birthdayHelper?.let { { Text(it) } },
            isError = birthdayIso.isNotBlank() && !birthdayValid,
            singleLine = true,
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !saving) { showBirthdayPicker = true },
        )
        if (showBirthdayPicker) {
            val birthdayPickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showBirthdayPicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            birthdayPickerState.selectedDateMillis?.let { selectedMillis ->
                                birthdayIso = utcMillisToIsoDate(selectedMillis)
                            }
                            showBirthdayPicker = false
                        },
                        enabled = birthdayPickerState.selectedDateMillis != null,
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBirthdayPicker = false }) {
                        Text("Cancel")
                    }
                },
            ) {
                DatePicker(state = birthdayPickerState)
            }
        }
        error?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = {
                if (!canSave) return@Button
                saving = true
                error = null
                scope.launch {
                    val result = repo.updateUserProfileBasics(
                        userId = userId,
                        firstName = firstName,
                        lastName = lastName,
                        birthdayIso = birthdayIso.trim(),
                    )
                    result.fold(
                        onSuccess = {
                            saving = false
                            onCompleted()
                        },
                        onFailure = { e ->
                            saving = false
                            error = e.message?.trim()?.take(200)?.ifBlank { "Could not save profile." }
                                ?: "Could not save profile."
                        },
                    )
                }
            },
            enabled = firstName.isNotBlank() && lastName.isNotBlank() && birthdayValid && !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Saving…" else "Save and continue")
        }
    }
}
