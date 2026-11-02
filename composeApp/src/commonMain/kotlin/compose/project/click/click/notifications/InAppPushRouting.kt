package compose.project.click.click.notifications

import compose.project.click.click.deeplink.AppDeepLink
import compose.project.click.click.deeplink.AppDeepLinkRouter
import compose.project.click.click.deeplink.EventDeepLinkRouter

/**
 * Opens a [PushRoute] from inside the app (Activity rows carry their push payload), through the same
 * routers a tapped notification uses. Returns false for [PushRoute.OpenApp] (nowhere to go).
 */
fun openPushRouteInApp(route: PushRoute): Boolean =
    when (route) {
        is PushRoute.DirectChat -> {
            ChatDeepLinkManager.setPendingChat(route.connectionId.ifBlank { route.chatId })
            true
        }
        is PushRoute.GroupChat -> {
            ChatDeepLinkManager.setPendingChat(route.chatId)
            true
        }
        is PushRoute.Event -> {
            EventDeepLinkRouter.setPendingBeaconId(route.beaconId)
            true
        }
        is PushRoute.Hub -> {
            ChatDeepLinkManager.setPendingCommunityHub(route.hubId)
            true
        }
        is PushRoute.Profile -> {
            ChatDeepLinkManager.setPendingProfile(route.userId)
            true
        }
        PushRoute.Connections -> {
            AppDeepLinkRouter.open(AppDeepLink.Clicks)
            true
        }
        is PushRoute.SharedDrop -> {
            AppDeepLinkRouter.open(AppDeepLink.SharedDrop(route.dropId))
            true
        }
        PushRoute.OpenApp -> false
    }
