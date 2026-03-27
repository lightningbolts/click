package compose.project.click.click.data

import io.github.jan.supabase.auth.user.UserInfo

private fun Map<String, Any?>?.rawMetaString(key: String): String? {
    if (this == null) return null
    val v = this[key] ?: return null
    val s = v.toString().removeSurrounding("\"").trim()
    return s.takeIf { it.isNotEmpty() }
}

/** Display string for UI: prefers split names, then full_name / legacy name. */
fun UserInfo.displayNameFromMetadata(): String? {
    val meta = userMetadata ?: return null
    val fn = meta.rawMetaString("first_name")
    val ln = meta.rawMetaString("last_name")
    val combined = listOfNotNull(fn, ln).joinToString(" ").trim()
    if (combined.isNotEmpty()) return combined
    return meta.rawMetaString("full_name") ?: meta.rawMetaString("name")
}
