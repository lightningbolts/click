package compose.project.click.click.data.models

/** A message the viewer scheduled with Send Later; [content] is decrypted on this device. */
data class ScheduledMessage(
    val id: String,
    val content: String,
    val sendAtEpochMs: Long,
)
