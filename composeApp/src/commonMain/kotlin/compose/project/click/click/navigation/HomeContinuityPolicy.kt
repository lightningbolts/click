package compose.project.click.click.navigation

/**
 * During a gesture commit AnimatedContent briefly composes both the outgoing route and Home.
 * The outgoing route must release movable Home in the same recomposition that Home becomes the
 * current route, otherwise two slots request the same movable subtree and the handoff can flash.
 */
internal fun shouldRenderHomeSwipeUnderlay(currentRoute: String): Boolean =
    currentRoute != NavigationItem.Home.route
