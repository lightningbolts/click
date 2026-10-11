@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.chat.ClickDropBubbleHost
import compose.project.click.click.viewmodel.ChatMessagesState
import compose.project.click.click.viewmodel.ChatViewModel
import compose.project.click.click.viewmodel.developAllClickDrops
import compose.project.click.click.viewmodel.developClickDrop
import compose.project.click.click.viewmodel.developClickDropsRevealedWhileVisible
import compose.project.click.click.viewmodel.requestDevelopedDropOriginal
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun rememberClickDropBubbleHost(viewModel: ChatViewModel): ClickDropBubbleHost =
    remember(viewModel) {
        ClickDropBubbleHost(
            controller = viewModel.clickDrops,
            originals = viewModel.dropOriginalBytes,
            onDevelop = { viewModel.developClickDrop(it) },
            requestOriginal = { viewModel.requestDevelopedDropOriginal(it) },
        )
    }

/** Live develop while this chat is on screen, and a calm note when developing fails. */
@Composable
internal fun ClickDropChatEffects(
    viewModel: ChatViewModel,
    onError: (String) -> Unit,
) {
    LaunchedEffect(viewModel) {
        while (isActive) {
            delay(1_000)
            viewModel.developClickDropsRevealedWhileVisible()
        }
    }
    val error by viewModel.clickDrops.error.collectAsState()
    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        viewModel.clickDrops.clearError()
        onError(message)
    }
}

/** "Develop all (N)" when more than one drop on screen is ready. */
@Composable
internal fun ClickDropDevelopAllChip(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.chatMessagesState.collectAsState()
    val developedAt by viewModel.clickDrops.developedAt.collectAsState()
    val developing by viewModel.clickDrops.developing.collectAsState()
    // Re-evaluated every few seconds so drops that reach zero join the count.
    val tick by produceState(0) {
        while (true) {
            delay(5_000)
            value++
        }
    }
    val ready =
        remember(state, developedAt, developing, tick) {
            val messages = (state as? ChatMessagesState.Success)?.messages?.map { it.message }.orEmpty()
            viewModel.clickDrops.readyDrops(messages).count { it.id !in developing }
        }
    AnimatedVisibility(visible = ready > 1, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            onClick = { viewModel.developAllClickDrops() },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
        ) {
            Text(
                text = "Develop all ($ready)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
