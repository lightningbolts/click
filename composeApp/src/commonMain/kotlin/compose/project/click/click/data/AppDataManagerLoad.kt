@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.auth.LocalSessionCache // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserAvailability // pragma: allowlist secret
import compose.project.click.click.data.models.resolveDisplayName // pragma: allowlist secret
import compose.project.click.click.data.realtime.RealtimeCoordinator // pragma: allowlist secret
import compose.project.click.click.data.repository.NotificationPreferences // pragma: allowlist secret
import compose.project.click.click.notifications.NotificationRuntimeState // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock

/**
 * Shared session + Realtime recovery used by foreground resume and offline→online reconnect.
 * Refreshes GoTrue, rebinds Realtime with a fresh JWT, and refreshes inbox/data when stale.
 */
internal fun AppDataManager.recoverSessionAndRealtime(
    reason: String,
    forceDataRefresh: Boolean,
) {
    if (_ghostModeEnabled.value) return
    val now = Clock.System.now().toEpochMilliseconds()
    if (now - lastForegroundRecoveryMs < FOREGROUND_RECOVERY_DEBOUNCE_MS) return
    lastForegroundRecoveryMs = now
    scope.launch {
        println("AppDataManager: session/realtime recovery ($reason)")
        val recoveryOk =
            runCatching {
                SupabaseForegroundRecovery.recoverAfterBackground(SupabaseConfig.client)
            }.onFailure { e ->
                println(
                    "AppDataManager: session recovery failed ($reason): ${e.redactedRestMessage()}",
                )
            }.getOrDefault(false)
        _foregroundRealtimeRecovery.emit(Unit)
        // RealtimeCoordinator.stop() runs inside recovery — re-subscribe with the fresh JWT.
        _currentUser.value?.id?.takeIf { it.isNotBlank() }?.let { uid ->
            runCatching { RealtimeCoordinator.ensureStarted(uid) }
        }

        val dataStale = now - lastRefreshTime > REFRESH_COOLDOWN_MS
        val inboxStale = !isInboxFeedFresh(now)
        if (forceDataRefresh || !recoveryOk || (dataStale && inboxStale)) {
            loadAllDataJob?.cancel()
            if (!forceDataRefresh && recoveryOk && inboxStale && !dataStale) {
                refreshInboxFromCoordinator(force = true)
            } else {
                startLoadAllDataJob()
            }
        } else if (recoveryOk && inboxStale) {
            refreshInboxFromCoordinator(force = true)
        }
    }
}

internal fun AppDataManager.startLoadAllDataJob() {
    loadAllDataJob?.cancel()
    loadAllDataJob =
        scope.launch {
            loadAllData()
        }
}

/**
 * Load all app data
 */
internal suspend fun AppDataManager.loadAllData() {
    _isLoading.value = true
    _error.value = null
    restoreCachedSnapshot()
    restoreActiveHubs()
    if (_currentUser.value != null) {
        _isDataLoaded.value = true
        _isLoading.value = false
    }

    try {
        // Get current user from auth (or fall back to restored cache / local session tokens).
        val authUser = authRepository.getCurrentUser()
        val cachedUserId = _currentUser.value?.id
        val localSessionUserId = LocalSessionCache.read(tokenStorage)?.userId
        if (authUser == null && cachedUserId.isNullOrBlank() && localSessionUserId.isNullOrBlank()) {
            println("AppDataManager: No auth user found")
            _isDataLoaded.value = true
            _isLoading.value = false
            return
        }

        val effectiveUserId = authUser?.id ?: cachedUserId ?: localSessionUserId!!

        println("AppDataManager: Loading data for user $effectiveUserId")

        requestMapDiscoveryPrefetch()

        withTimeout(STARTUP_TIMEOUT_MS) {
            val snapshotDeferred =
                async {
                    runCatching { supabaseRepository.fetchUserConnectionsSnapshot(effectiveUserId) }
                        .onFailure { println("AppDataManager: Connection snapshot fetch failed: ${it.message}") }
                        .getOrNull()
                }

            val meta = authUser?.userMetadata

            fun metaStr(key: String) =
                meta
                    ?.get(key)
                    ?.toString()
                    ?.removeSurrounding("\"")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            val metaFirst = metaStr("first_name")
            val metaLast = metaStr("last_name")
            val metaBirthday = metaStr("birthday")
            val cachedSessionUser = _currentUser.value?.takeIf { it.id == effectiveUserId }
            val cachedImage = cachedSessionUser?.image?.trim()?.takeIf { it.isNotEmpty() }
            val authDisplay = authUser?.displayNameFromMetadata()
            println(
                "AppDataManager: Auth metadata — first/last: $metaFirst / $metaLast, display: $authDisplay",
            )

            // Fetch user data from database
            var user = supabaseRepository.fetchUserById(effectiveUserId)
            println("AppDataManager: Fetched user from DB: ${user?.name}")

            if (user == null) {
                val resolvedFirst = metaFirst ?: cachedSessionUser?.firstName
                val resolvedLast = metaLast ?: cachedSessionUser?.lastName
                val resolvedBirthday = metaBirthday ?: cachedSessionUser?.birthday
                val resolvedImage = cachedImage
                val offlineUser = cachedSessionUser?.takeIf { it.id == effectiveUserId }
                if (offlineUser != null && authUser == null) {
                    user = offlineUser
                } else {
                    // Create user in database if not exists
                    val newUser =
                        User(
                            id = effectiveUserId,
                            name =
                                resolveDisplayName(
                                    firstName = resolvedFirst,
                                    lastName = resolvedLast,
                                    fullName = metaStr("full_name") ?: authDisplay,
                                    name = null,
                                    email = authUser?.email,
                                ),
                            email = authUser?.email,
                            image = resolvedImage,
                            createdAt = Clock.System.now().toEpochMilliseconds(),
                            lastPolled = null,
                            firstName = resolvedFirst,
                            lastName = resolvedLast,
                            birthday = resolvedBirthday,
                            connections = emptyList(),
                            paired_with = emptyList(),
                            connection_today = 0,
                            last_paired = null,
                        )
                    println("AppDataManager: Creating new user in DB: ${newUser.name}")
                    supabaseRepository.upsertUser(newUser)
                    user = newUser
                }
            } else {
                val desiredName =
                    resolveDisplayName(
                        firstName = metaFirst ?: user.firstName,
                        lastName = metaLast ?: user.lastName,
                        fullName = metaStr("full_name") ?: authDisplay,
                        name = user.name,
                        email = authUser?.email ?: user.email,
                    )
                val desiredEmail = authUser?.email ?: user.email
                val resolvedImage = user.image?.trim()?.takeIf { it.isNotEmpty() } ?: cachedImage
                val syncedUser =
                    user.copy(
                        name = desiredName,
                        email = desiredEmail,
                        image = resolvedImage,
                        firstName = metaFirst ?: user.firstName ?: cachedSessionUser?.firstName,
                        lastName = metaLast ?: user.lastName ?: cachedSessionUser?.lastName,
                        birthday = metaBirthday ?: user.birthday ?: cachedSessionUser?.birthday,
                    )
                if (syncedUser != user) {
                    println("AppDataManager: Syncing current user profile to users table: ${syncedUser.name}")
                    supabaseRepository.upsertUser(syncedUser)
                    user = syncedUser
                }
            }

            _currentUser.value = user
            println("AppDataManager: Current user set to: ${user.name}")
            runCatching { chatRepository.startGlobalPresence(user.id) }
                .onFailure { e -> println("AppDataManager: Global presence start failed: ${e.redactedRestMessage()}") }
            startPresenceHeartbeat(user.id)
            startAggressiveBackgroundChatSync(user.id)

            // Interest tags: await during startup so Settings renders instantly (no shimmer).
            val interestsDeferred =
                async {
                    runCatching {
                        supabaseRepository
                            .fetchUserInterests(user.id)
                            .getOrNull()
                            ?.tags
                            .orEmpty()
                    }.getOrDefault(emptyList())
                }

            // Load location preferences from Supabase
            runCatching { supabaseRepository.fetchLocationPreferences(user.id) }
                .onSuccess { _locationPreferences.value = it }
                .onFailure { println("AppDataManager: Failed to load location preferences: ${it.message}") }

            val localNotificationPreferences =
                NotificationPreferences(
                    messagePushEnabled = tokenStorage.getMessageNotificationsEnabled() ?: true,
                    callPushEnabled = tokenStorage.getCallNotificationsEnabled() ?: true,
                )
            _notificationPreferences.value = localNotificationPreferences
            NotificationRuntimeState.setNotificationPreferences(
                messageEnabled = localNotificationPreferences.messagePushEnabled,
                callEnabled = localNotificationPreferences.callPushEnabled,
                eventReminderEnabled = localNotificationPreferences.eventReminderPushEnabled,
                availabilityMatchEnabled = localNotificationPreferences.availabilityMatchPushEnabled,
                hubMessageEnabled = localNotificationPreferences.hubMessagePushEnabled,
                eventTeaserEnabled = localNotificationPreferences.eventTeaserPushEnabled,
                reconnectNudgeEnabled = localNotificationPreferences.reconnectNudgePushEnabled,
            )

            // Never trigger an OS permission prompt or device-token registration at login. The
            // Settings toggle is the intentional, contextual entry point for notification setup.

            scope.launch {
                val remotePreferences = notificationPreferencesRepository.fetchPreferences(user.id)
                _notificationPreferences.value = remotePreferences
                NotificationRuntimeState.setNotificationPreferences(
                    messageEnabled = remotePreferences.messagePushEnabled,
                    callEnabled = remotePreferences.callPushEnabled,
                    eventReminderEnabled = remotePreferences.eventReminderPushEnabled,
                    availabilityMatchEnabled = remotePreferences.availabilityMatchPushEnabled,
                    hubMessageEnabled = remotePreferences.hubMessagePushEnabled,
                    eventTeaserEnabled = remotePreferences.eventTeaserPushEnabled,
                    reconnectNudgeEnabled = remotePreferences.reconnectNudgePushEnabled,
                )
                tokenStorage.saveMessageNotificationsEnabled(remotePreferences.messagePushEnabled)
                tokenStorage.saveCallNotificationsEnabled(remotePreferences.callPushEnabled)

                // Remote preference hydration is not user intent. Do not turn it into an OS
                // notification prompt or an implicit token-registration side effect.
            }

            // Load availability from local storage first for immediate display
            val localFreeThisWeek = tokenStorage.getFreeThisWeek()
            if (localFreeThisWeek != null) {
                // Use local value immediately
                _userAvailability.value =
                    UserAvailability(
                        userId = user.id,
                        isFreeThisWeek = localFreeThisWeek,
                        lastUpdated = Clock.System.now().toEpochMilliseconds(),
                    )
                println("AppDataManager: Loaded local availability: isFreeThisWeek=$localFreeThisWeek")
            }

            coroutineScope {
                val availabilityDeferred =
                    async {
                        runCatching { supabaseRepository.fetchUserAvailability(user.id) }
                            .onFailure { println("AppDataManager: Availability fetch failed: ${it.message}") }
                            .getOrNull()
                    }

                // Prioritize connections and connected-user hydration so the Home/Map/Chats
                // screens are ready before slower auxiliary startup work completes.
                val snapshot = snapshotDeferred.await()
                if (snapshot != null) {
                    applyFetchedConnectionSnapshot(snapshot)
                }

                val interestTags = interestsDeferred.await()
                if (_currentUser.value?.id == user.id) {
                    _currentUser.value = _currentUser.value?.copy(tags = interestTags)
                    _userInterestTags.value = interestTags
                }

                _isDataLoaded.value = true
                lastRefreshTime = Clock.System.now().toEpochMilliseconds()
                persistSnapshot()
                startSilentChatPrefetch(user.id)

                // Keep first paint fast: hydrate connected users in background instead of
                // blocking Home readiness on this network call.
                scope.launch {
                    if (_currentUser.value?.id == user.id) {
                        runCatching { refreshConnectedUsers(_connections.value, user.id) }
                            .onFailure { e ->
                                println("AppDataManager: Background connected-user hydration failed: ${e.redactedRestMessage()}")
                            }.onSuccess {
                                startBackgroundProfilePrefetch(
                                    viewerUserId = user.id,
                                    peerUserIds = _connectedUsers.value.keys.toList(),
                                )
                            }
                    }
                }

                // Apply availability after the primary connection data is visible.
                val availability = availabilityDeferred.await()
                if (availability != null) {
                    _userAvailability.value = availability
                    tokenStorage.saveFreeThisWeek(availability.isFreeThisWeek)
                    println("AppDataManager: Synced availability from Supabase: isFreeThisWeek=${availability.isFreeThisWeek}")
                } else if (localFreeThisWeek == null) {
                    println("AppDataManager: No availability found locally or on server")
                }
            }
        }
    } catch (e: CancellationException) {
        // Replacing an in-flight load (foreground recovery, refresh) cancels this job; must not
        // treat that as an offline / sync failure or the banner shows until the next full load.
        throw e
    } catch (e: Exception) {
        println("Error loading app data: ${e.redactedRestMessage()}")
        // Do not printStackTrace() — RestException.message embeds Authorization/apikey headers.
        _error.value = mapStartupErrorMessage(e.redactedRestMessage())
        _isDataLoaded.value = true
    } finally {
        _isLoading.value = false
    }
}
