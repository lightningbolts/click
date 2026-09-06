package compose.project.click.click.navigation

/** Manual route stack used by the main shell's forward and back navigation. */
internal class RouteHistory(initialRoute: String) {
    private val routes = mutableListOf(initialRoute)

    val currentRoute: String
        get() = routes.last()

    fun navigateTo(route: String): Boolean {
        if (route == currentRoute) {
            return false
        }
        routes.add(route)
        return true
    }

    fun navigateBack(): String? {
        if (routes.size <= 1) {
            return null
        }
        routes.removeAt(routes.lastIndex)
        return currentRoute
    }

    fun resetTo(route: String) {
        routes.clear()
        routes.add(route)
    }
}
