package compose.project.click.click.encounter

import platform.Foundation.NSUserDefaults

actual object EncounterTetherWidgetBridge {
    actual fun updateRecentEncounter(encounterId: String?, peerDisplayName: String?) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (encounterId.isNullOrBlank()) {
            defaults.removeObjectForKey("recent_encounter_id")
            defaults.removeObjectForKey("peer_display_name")
        } else {
            defaults.setObject(encounterId, forKey = "recent_encounter_id")
            defaults.setObject(peerDisplayName ?: "", forKey = "peer_display_name")
        }
        defaults.synchronize()
    }
}
