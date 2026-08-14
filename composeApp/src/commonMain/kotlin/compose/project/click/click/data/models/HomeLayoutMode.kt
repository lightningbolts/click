package compose.project.click.click.data.models // pragma: allowlist secret

enum class HomeLayoutMode {
    PILE,
    LINEAR,
    ;

    companion object {
        fun fromStored(raw: String?): HomeLayoutMode =
            when (raw?.trim()?.lowercase()) {
                "linear", "list" -> LINEAR
                else -> PILE
            }
    }
}
