package compose.project.click.click

import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.PushTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val pushTokenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val pushTokenRepository = PushTokenRepository()

fun savePushToken(token: String, platform: String) {
    pushTokenScope.launch {
        val currentUserId = AppDataManager.currentUser.value?.id ?: AuthRepository().getCurrentUser()?.id
        if (currentUserId.isNullOrBlank()) {
            println("savePushToken: Skipping upload because no authenticated user is available")
            return@launch
        }
        pushTokenRepository.savePushToken(
            userId = currentUserId,
            token = token,
            platform = platform
        )
    }
}