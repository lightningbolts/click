package compose.project.click.click.calls

/**
 * Decides whether [CallManager.startCall] should resume after a runtime permission dialog.
 *
 * The Activity Result map can be empty when [MainActivity] is recreated mid-dialog.
 * That must not be treated as a denial — the OS grant set is the source of truth.
 */
object CallPermissionResultPolicy {
    fun shouldResumeCall(
        requiredPermissions: List<String>,
        launcherResults: Map<String, Boolean>,
        osGrantedPermissions: Set<String>,
    ): Boolean {
        if (requiredPermissions.isEmpty()) return true
        return requiredPermissions.all { permission ->
            permission in osGrantedPermissions || launcherResults[permission] == true
        }
    }
}
