package compose.project.click.click

import android.os.Build
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import compose.project.click.click.data.storage.initTokenStorage
import compose.project.click.click.calls.initCallManager
import compose.project.click.click.calls.CallInvite
import compose.project.click.click.calls.CallSessionManager
import compose.project.click.click.notifications.ChatDeepLinkManager
import compose.project.click.click.qr.toHubIdFromClickHubUrl
import compose.project.click.click.notifications.initPushNotificationService
import compose.project.click.click.utils.initLocationService
import compose.project.click.click.ui.chat.AndroidChatImageSaveContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        configureScreenWakeForCalls(intent)

        AndroidChatImageSaveContext.applicationContext = applicationContext

        // Initialize token storage with application context
        initTokenStorage(applicationContext)

        // Initialize location service with application context
        initLocationService(applicationContext)

        initCallManager(applicationContext, this)

        initPushNotificationService(applicationContext, this)

        handleIncomingCallIntent(intent)
        handleChatDeepLinkIntent(intent)
        handleCommunityHubViewIntent(intent)

        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        compose.project.click.click.notifications.AndroidPushNotificationRuntime.setAppInForeground(true)
    }

    override fun onPause() {
        compose.project.click.click.notifications.AndroidPushNotificationRuntime.setAppInForeground(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureScreenWakeForCalls(intent)
        handleIncomingCallIntent(intent)
        handleChatDeepLinkIntent(intent)
        handleCommunityHubViewIntent(intent)
    }

    private fun handleCommunityHubViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uriString = intent.dataString ?: return
        val hubId = uriString.toHubIdFromClickHubUrl() ?: return
        ChatDeepLinkManager.setPendingCommunityHub(hubId)
    }

    private fun handleChatDeepLinkIntent(intent: Intent?) {
        if (intent?.action != ACTION_VIEW_CHAT) return
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        val connectionId = intent.getStringExtra(EXTRA_CHAT_CONNECTION_ID)
        val deepLinkId = chatId?.takeIf { it.isNotBlank() } ?: connectionId ?: return
        ChatDeepLinkManager.setPendingChat(deepLinkId)
    }

    private fun configureScreenWakeForCalls(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_ACCEPT_CALL && action != ACTION_DECLINE_CALL && action != ACTION_VIEW_CALL) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun handleIncomingCallIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_ACCEPT_CALL && action != ACTION_DECLINE_CALL && action != ACTION_VIEW_CALL) return

        val invite = intent.toCallInvite() ?: return
        when (action) {
            ACTION_ACCEPT_CALL -> CallSessionManager.receiveIncomingPush(invite, autoAnswer = true)
            ACTION_DECLINE_CALL -> CallSessionManager.receiveIncomingPush(invite, autoDecline = true)
            ACTION_VIEW_CALL -> CallSessionManager.receiveIncomingPush(invite)
        }
    }

    companion object {
        const val ACTION_VIEW_CALL = "compose.project.click.click.action.VIEW_CALL"
        const val ACTION_ACCEPT_CALL = "compose.project.click.click.action.ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "compose.project.click.click.action.DECLINE_CALL"
        const val ACTION_VIEW_CHAT = "compose.project.click.click.action.VIEW_CHAT"

        private const val EXTRA_CHAT_ID = "extra_chat_id"
        private const val EXTRA_CHAT_CONNECTION_ID = "extra_chat_connection_id"
        private const val EXTRA_CALL_ID = "extra_call_id"
        private const val EXTRA_CONNECTION_ID = "extra_connection_id"
        private const val EXTRA_ROOM_NAME = "extra_room_name"
        private const val EXTRA_CALLER_ID = "extra_caller_id"
        private const val EXTRA_CALLER_NAME = "extra_caller_name"
        private const val EXTRA_CALLEE_ID = "extra_callee_id"
        private const val EXTRA_CALLEE_NAME = "extra_callee_name"
        private const val EXTRA_VIDEO_ENABLED = "extra_video_enabled"
        private const val EXTRA_CREATED_AT = "extra_created_at"

        fun createIncomingCallIntent(context: Context, action: String, invite: CallInvite): Intent {
            return Intent(context, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_CALL_ID, invite.callId)
                putExtra(EXTRA_CONNECTION_ID, invite.connectionId)
                putExtra(EXTRA_ROOM_NAME, invite.roomName)
                putExtra(EXTRA_CALLER_ID, invite.callerId)
                putExtra(EXTRA_CALLER_NAME, invite.callerName)
                putExtra(EXTRA_CALLEE_ID, invite.calleeId)
                putExtra(EXTRA_CALLEE_NAME, invite.calleeName)
                putExtra(EXTRA_VIDEO_ENABLED, invite.videoEnabled)
                putExtra(EXTRA_CREATED_AT, invite.createdAt)
            }
        }

        fun createChatDeepLinkIntent(
            context: Context,
            chatId: String = "",
            connectionId: String = "",
        ): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = ACTION_VIEW_CHAT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_CHAT_CONNECTION_ID, connectionId)
            }
        }

        private fun Intent.toCallInvite(): CallInvite? {
            val callId = getStringExtra(EXTRA_CALL_ID) ?: return null
            val connectionId = getStringExtra(EXTRA_CONNECTION_ID) ?: return null
            val roomName = getStringExtra(EXTRA_ROOM_NAME) ?: return null
            val callerId = getStringExtra(EXTRA_CALLER_ID) ?: return null
            val callerName = getStringExtra(EXTRA_CALLER_NAME) ?: return null
            val calleeId = getStringExtra(EXTRA_CALLEE_ID) ?: return null
            val calleeName = getStringExtra(EXTRA_CALLEE_NAME) ?: return null
            return CallInvite(
                callId = callId,
                connectionId = connectionId,
                roomName = roomName,
                callerId = callerId,
                callerName = callerName,
                calleeId = calleeId,
                calleeName = calleeName,
                videoEnabled = getBooleanExtra(EXTRA_VIDEO_ENABLED, false),
                createdAt = getLongExtra(EXTRA_CREATED_AT, 0L),
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}