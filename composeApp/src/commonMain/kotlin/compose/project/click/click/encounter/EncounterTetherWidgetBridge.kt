package compose.project.click.click.encounter

/**
 * Home-screen widget hook: surfaces the active recent encounter for 1-tap tether pings.
 * Platform actuals write to shared app-group / widget state.
 */
expect object EncounterTetherWidgetBridge {
    fun updateRecentEncounter(encounterId: String?, peerDisplayName: String?)
}
