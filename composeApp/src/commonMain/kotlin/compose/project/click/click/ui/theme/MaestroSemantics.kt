package compose.project.click.click.ui.theme

import androidx.compose.ui.Modifier

/** Expose Compose testTags as Android resource ids for Maestro. No-op on iOS. */
expect fun Modifier.maestroTestTagsAsResourceId(): Modifier
