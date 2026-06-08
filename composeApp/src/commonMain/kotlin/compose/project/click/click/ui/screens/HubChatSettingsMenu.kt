package compose.project.click.click.ui.screens

enum class HubSettingsMenuItem {
    Leave,
    Edit,
    Delete,
}

fun visibleHubSettingsMenuItems(currentUserId: String, creatorId: String?): List<HubSettingsMenuItem> {
    val isCreator = creatorId != null && currentUserId == creatorId
    return if (isCreator) {
        listOf(HubSettingsMenuItem.Leave, HubSettingsMenuItem.Edit, HubSettingsMenuItem.Delete)
    } else {
        listOf(HubSettingsMenuItem.Leave)
    }
}
