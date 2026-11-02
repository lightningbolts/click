package compose.project.click.click.viewmodel

import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropAudience
import compose.project.click.click.collaboration.SharedDropUser
import compose.project.click.click.data.api.DropDevelopResultDto
import compose.project.click.click.data.api.ReactionTarget
import compose.project.click.click.data.api.ReactionsService
import compose.project.click.click.data.api.ReactionsState
import compose.project.click.click.data.api.Reactor
import compose.project.click.click.data.api.SharedDropArchivePage
import compose.project.click.click.data.api.SharedDropCreateBody
import compose.project.click.click.data.api.SharedDropPostException
import compose.project.click.click.data.api.SharedDropsService
import compose.project.click.click.data.realtime.LiveBatch
import compose.project.click.click.data.realtime.LiveKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSharedDropsService : SharedDropsService {
    var stripResult: Result<List<SharedDrop>> = Result.success(emptyList())
    var shareResults = ArrayDeque<Result<SharedDrop>>()
    val shared = mutableListOf<SharedDropCreateBody>()
    var developResult: Result<DropDevelopResultDto?> = Result.success(null)
    val developed = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val downloads = mutableMapOf<String, ByteArray>()
    val archivePages = mutableMapOf<String?, Result<SharedDropArchivePage>>()
    val archiveCalls = mutableListOf<String?>()

    override suspend fun strip() = stripResult

    override suspend fun archive(before: String?): Result<SharedDropArchivePage> {
        archiveCalls += before
        return archivePages[before] ?: Result.failure(Exception("offline"))
    }

    override suspend fun share(body: SharedDropCreateBody): Result<SharedDrop> {
        shared += body
        return shareResults.removeFirst()
    }

    override suspend fun delete(id: String): Result<Unit> {
        deleted += id
        return Result.success(Unit)
    }

    override suspend fun develop(id: String): Result<DropDevelopResultDto?> {
        developed += id
        return developResult
    }

    override suspend fun report(
        id: String,
        reason: String,
    ) = Result.success(Unit)

    override suspend fun download(url: String): Result<ByteArray> =
        downloads[url]?.let { Result.success(it) } ?: Result.failure(Exception("404"))
}

private class FakeReactions : ReactionsService {
    var reactResult: Result<ReactionsState> = Result.success(ReactionsState())
    var loadResult: Result<ReactionsState> = Result.success(ReactionsState())
    val reacted = mutableListOf<Pair<String, String?>>()

    override suspend fun reactions(
        target: ReactionTarget,
        id: String,
    ) = loadResult

    override suspend fun react(
        target: ReactionTarget,
        id: String,
        emoji: String?,
    ): Result<ReactionsState> {
        assertEquals(ReactionTarget.SHARED_DROP, target)
        reacted += id to emoji
        return reactResult
    }
}

private class FakeReplies : SharedDropReplySender {
    var succeed = true
    val sent = mutableListOf<Triple<String, String, JsonObject>>()

    override suspend fun send(
        connectionId: String,
        userId: String,
        text: String,
        metadata: JsonObject,
    ): Boolean {
        sent += Triple(connectionId, text, metadata)
        return succeed
    }
}

private class FakeDisk : SharedDropsDisk {
    val files = mutableMapOf<String, ByteArray>()

    override fun read(
        key: String,
        extension: String,
    ) = files["$key.$extension"]

    override fun write(
        key: String,
        bytes: ByteArray,
        extension: String,
    ) {
        files["$key.$extension"] = bytes
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SharedDropsViewModelTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val past = "2026-10-01T11:00:00Z"
    private val future = "2026-10-01T13:00:00Z"

    private fun drop(
        id: String,
        user: String = "maya",
        mine: Boolean = false,
        reveal: String = past,
        developed: String? = null,
        preview: String? = null,
        original: String? = null,
        connection: String? = null,
        reactions: ReactionsState? = null,
        created: String? = null,
    ) = SharedDrop(
        id = id,
        user = SharedDropUser(user, user),
        isMine = mine,
        revealAtIso = reveal,
        developedAtIso = developed,
        previewUrl = preview,
        originalUrl = original,
        connectionId = connection,
        reactions = reactions,
        createdAt = created,
    )

    private fun TestScope.viewModel(
        service: FakeSharedDropsService,
        disk: FakeDisk = FakeDisk(),
        user: () -> String? = { "me" },
        preview: ByteArray? = byteArrayOf(7),
        reactions: FakeReactions = FakeReactions(),
        replies: FakeReplies = FakeReplies(),
        live: Flow<LiveBatch> = emptyFlow(),
        originalsBudget: Long = SHARED_DROP_ORIGINALS_BUDGET_BYTES,
    ): SharedDropsViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return SharedDropsViewModel(
            service = service,
            disk = disk,
            currentUserId = user,
            isEnabled = { true },
            makePreview = { preview },
            imageSize = { 30 to 40 },
            now = { now },
            reactions = reactions,
            replySender = replies,
            liveHints = live,
            originalsBudgetBytes = originalsBudget,
        )
    }

    @Test
    fun developedOriginalsInMemoryStayWithinTheBudgetAndReloadFromDisk() =
        runTest {
            try {
                val disk = FakeDisk()
                listOf("a", "b", "c").forEach { disk.files["shared-drop-me-$it-original.jpg"] = ByteArray(4) { 1 } }
                val vm = viewModel(FakeSharedDropsService(), disk, originalsBudget = 10)
                val drops = listOf("a", "b", "c").associateWith { drop(it, developed = past) }
                vm.warmImages(drops.getValue("a"))
                vm.warmImages(drops.getValue("b"))
                // Shown again: "a" is now the most recent, so "b" goes first.
                vm.warmImages(drops.getValue("a"))
                vm.warmImages(drops.getValue("c"))
                assertEquals(setOf("a", "c"), vm.originals.value.keys)
                // Evicted from memory only: it comes back from disk when shown again.
                vm.warmImages(drops.getValue("b"))
                assertEquals(setOf("c", "b"), vm.originals.value.keys)
                assertEquals(3, disk.files.count { it.key.endsWith("-original.jpg") })
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun refreshShowsTheLiveStripLoadsTeasersAndCachesItWithoutSignedUrls() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                service.stripResult = Result.success(listOf(drop("a", preview = "https://p/a", original = "https://o/a")))
                service.downloads["https://p/a"] = byteArrayOf(1)
                val disk = FakeDisk()
                val vm = viewModel(service, disk)
                vm.refresh()
                assertEquals(
                    listOf("a"),
                    vm.state.value.drops
                        .map { it.id },
                )
                assertTrue(vm.state.value.loaded)
                assertContentEquals(byteArrayOf(1), vm.previews.value["a"])
                val cached = disk.files["shared-drops-strip-me.json"]!!.decodeToString()
                assertFalse(cached.contains("https://"))
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun theLastStripPaintsFromDiskWhenTheNetworkFails() =
        runTest {
            try {
                val disk = FakeDisk()
                val first = FakeSharedDropsService().apply { stripResult = Result.success(listOf(drop("cached"))) }
                viewModel(first, disk).refresh()
                val offline = FakeSharedDropsService().apply { stripResult = Result.failure(Exception("offline")) }
                val vm = viewModel(offline, disk)
                vm.refresh()
                assertEquals(
                    listOf("cached"),
                    vm.state.value.drops
                        .map { it.id },
                )
                // Another account never sees it.
                val other = viewModel(offline, disk, user = { "someone-else" })
                other.refresh()
                assertTrue(
                    other.state.value.drops
                        .isEmpty(),
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun developingAReadyDropMarksItDevelopedAndFetchesTheOriginal() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                service.stripResult = Result.success(listOf(drop("a")))
                service.developResult =
                    Result.success(
                        DropDevelopResultDto(kind = "shared", id = "a", status = "developed", developedAt = past, url = "https://o/a"),
                    )
                service.downloads["https://o/a"] = byteArrayOf(5, 5)
                val disk = FakeDisk()
                val vm = viewModel(service, disk)
                vm.refresh()
                val ready =
                    vm.state.value.drops
                        .single()
                assertEquals(ClickDropDevelopState.Ready, ready.state(now))
                vm.develop(ready)
                assertEquals(listOf("a"), service.developed)
                assertEquals(
                    ClickDropDevelopState.Developed,
                    vm.state.value.drops
                        .single()
                        .state(now),
                )
                assertContentEquals(byteArrayOf(5, 5), vm.originals.value["a"])
                assertNotNull(disk.files["shared-drop-me-a-original.jpg"])
                assertTrue(
                    vm.state.value.developing
                        .isEmpty(),
                )
                // Pending drops never call develop.
                vm.develop(drop("b", reveal = future))
                assertEquals(listOf("a"), service.developed)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun aFailedDevelopSaysSoAndLeavesTheDropReady() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                service.stripResult = Result.success(listOf(drop("a")))
                service.developResult = Result.failure(Exception("500"))
                val vm = viewModel(service)
                vm.refresh()
                vm.develop(
                    vm.state.value.drops
                        .single(),
                )
                assertEquals(SHARED_DROP_DEVELOP_FAILED, vm.state.value.notice)
                assertEquals(
                    ClickDropDevelopState.Ready,
                    vm.state.value.drops
                        .single()
                        .state(now),
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun aFailedShareRetriesWithTheSameClientDropId() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                service.shareResults += Result.failure(Exception("timeout"))
                service.shareResults += Result.success(drop("new", user = "me", mine = true, reveal = future))
                val vm = viewModel(service)
                vm.refresh()
                vm.share(byteArrayOf(1, 2), SharedDropAudience.CORE, " hi ")
                assertEquals(SHARED_DROP_SHARE_FAILED, vm.state.value.postError)
                assertNotNull(vm.state.value.pendingPost)
                vm.retryShare()
                assertEquals(2, service.shared.size)
                assertEquals(service.shared[0].clientDropId, service.shared[1].clientDropId)
                assertEquals("hi", service.shared[1].caption)
                assertEquals("core", service.shared[1].audience)
                assertEquals(30, service.shared[1].width)
                assertNull(vm.state.value.postError)
                assertEquals(
                    listOf("new"),
                    vm.state.value.drops
                        .map { it.id },
                )
                // You already have your own photo.
                assertContentEquals(byteArrayOf(1, 2), vm.originals.value["new"])
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun theDailyCapCannotBeRetried() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                service.shareResults += Result.failure(SharedDropPostException.CapReached())
                val vm = viewModel(service)
                vm.refresh()
                vm.share(byteArrayOf(1), SharedDropAudience.ALL, null)
                assertTrue(
                    vm.state.value.postError!!
                        .contains("Try again tomorrow"),
                )
                assertNull(vm.state.value.pendingPost)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun aPhotoWithoutAPreviewIsRefusedBeforeUploading() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                val vm = viewModel(service, preview = null)
                vm.refresh()
                vm.share(byteArrayOf(1), SharedDropAudience.ALL, null)
                assertTrue(service.shared.isEmpty())
                assertEquals(SharedDropPostException.InvalidPhoto().message, vm.state.value.postError)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun deletingRemovesTheDrop() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                service.stripResult = Result.success(listOf(drop("a", mine = true), drop("b")))
                val vm = viewModel(service)
                vm.refresh()
                vm.delete(
                    vm.state.value.drops
                        .first(),
                )
                assertEquals(listOf("a"), service.deleted)
                assertEquals(
                    listOf("b"),
                    vm.state.value.drops
                        .map { it.id },
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun listHelpersKeepOneCopyOfEachDrop() {
        val list = listOf(drop("a"), drop("b"))
        assertEquals(listOf("c", "a", "b"), list.withShared(drop("c")).map { it.id })
        assertEquals(listOf("a", "b"), list.withShared(drop("a")).map { it.id })
        val developed = list.withDeveloped("b", past, "https://o/b")
        assertEquals(past, developed[1].developedAtIso)
        assertEquals("https://o/b", developed[1].originalUrl)
        assertNull(developed[0].developedAtIso)
        assertEquals("https://o/b", developed.withDeveloped("b", past, null)[1].originalUrl)
    }

    @Test
    fun clientDropIdsAreVersion4Uuids() {
        val id = newSharedDropClientId(Random(42))
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(id), id)
    }

    @Test
    fun theStripTicksWhenTheNextDropReveals() {
        assertEquals(60_000, sharedDropsNextTickMs(listOf(drop("a")), now))
        // Never longer than a minute, so countdowns stay fresh.
        assertEquals(60_250, sharedDropsNextTickMs(listOf(drop("a", reveal = "2026-10-01T12:05:00Z")), now))
        assertEquals(30_250, sharedDropsNextTickMs(listOf(drop("a", reveal = "2026-10-01T12:00:30Z")), now))
        assertEquals(750, sharedDropsNextTickMs(listOf(drop("a", reveal = "2026-10-01T12:00:00.100Z")), now))
    }

    @Test
    fun reactingShowsAtOnceThenAnswersInTheChatWithTheDrop() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                val d = drop("a", developed = past, connection = "c1")
                service.stripResult = Result.success(listOf(d))
                val reactions = FakeReactions()
                val saved = ReactionsState(mine = "🔥", reactions = listOf(Reactor("u2", "Sam Lee", null, "❤️")))
                reactions.reactResult = Result.success(saved)
                val replies = FakeReplies()
                val vm = viewModel(service, reactions = reactions, replies = replies)
                vm.refresh()
                vm.react(d, "🔥")
                assertEquals(listOf<Pair<String, String?>>("a" to "🔥"), reactions.reacted)
                assertEquals(
                    saved,
                    vm.state.value.drops
                        .single()
                        .reactions,
                )
                val (connection, text, metadata) = replies.sent.single()
                assertEquals("c1", connection)
                assertEquals("🔥", text)
                val wire = metadata["drop_reply"] as JsonObject
                assertEquals(JsonPrimitive("a"), wire["id"])
                assertEquals(JsonPrimitive(true), wire["reaction"])
                assertEquals("Reaction sent to maya", vm.state.value.notice)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun aFailedReactionPutsTheOldOneBackAndTakingItBackSendsNothing() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                val before = ReactionsState(mine = "😂")
                val d = drop("a", developed = past, connection = "c1", reactions = before)
                service.stripResult = Result.success(listOf(d))
                val reactions = FakeReactions()
                reactions.reactResult = Result.failure(Exception("500"))
                val replies = FakeReplies()
                val vm = viewModel(service, reactions = reactions, replies = replies)
                vm.refresh()
                vm.react(d, "🔥")
                assertEquals(
                    before,
                    vm.state.value.drops
                        .single()
                        .reactions,
                )
                reactions.reactResult = Result.success(ReactionsState())
                vm.react(d, null)
                assertEquals(listOf("a" to "🔥", "a" to null), reactions.reacted)
                assertNull(
                    vm.state.value.drops
                        .single()
                        .reactions
                        ?.mine,
                )
                assertTrue(replies.sent.isEmpty())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun ownDropsAreNeverReactedOrRepliedTo() =
        runTest {
            try {
                val reactions = FakeReactions()
                val replies = FakeReplies()
                val vm = viewModel(FakeSharedDropsService(), reactions = reactions, replies = replies)
                val mine = drop("a", mine = true, developed = past, connection = "c1")
                vm.react(mine, "🔥")
                vm.sendReply(mine, "hi")
                assertTrue(reactions.reacted.isEmpty())
                assertTrue(replies.sent.isEmpty())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun aReplyGoesToThePostersChatAndSaysWhetherItWent() =
        runTest {
            try {
                val replies = FakeReplies()
                val vm = viewModel(FakeSharedDropsService(), replies = replies)
                val d = drop("a", developed = past, connection = "c1")
                vm.sendReply(d, "  love this  ")
                assertEquals("love this", replies.sent.single().second)
                assertEquals(JsonPrimitive(false), (replies.sent.single().third["drop_reply"] as JsonObject)["reaction"])
                assertEquals("Sent to maya", vm.state.value.notice)
                replies.succeed = false
                vm.sendReply(d, "again")
                assertEquals("Couldn't send. Try again.", vm.state.value.notice)
                vm.sendReply(d.copy(connectionId = null), "nowhere")
                vm.sendReply(d, "   ")
                assertEquals(2, replies.sent.size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun theArchivePagesWithoutDuplicatesAndStopsAtTheEnd() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                service.archivePages[null] = Result.success(SharedDropArchivePage(listOf(drop("a"), drop("b")), nextBefore = "t1"))
                service.archivePages["t1"] = Result.success(SharedDropArchivePage(listOf(drop("b"), drop("c")), nextBefore = null))
                val vm = viewModel(service)
                vm.loadArchive()
                assertTrue(vm.state.value.archiveLoaded)
                assertEquals("t1", vm.state.value.archiveNextBefore)
                vm.loadMoreArchive()
                assertEquals(
                    listOf("a", "b", "c"),
                    vm.state.value.archive
                        .map { it.id },
                )
                assertNull(vm.state.value.archiveNextBefore)
                vm.loadMoreArchive()
                assertEquals(listOf(null, "t1"), service.archiveCalls)
                assertEquals(
                    "b",
                    vm.state.value
                        .drop("b")
                        ?.id,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun archiveFailuresSayWhatTheyCan() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                val vm = viewModel(service)
                vm.loadArchive()
                assertEquals(SHARED_DROP_ARCHIVE_FAILED, vm.state.value.archiveError)
                assertFalse(vm.state.value.archiveLoaded)
                service.archivePages[null] = Result.success(SharedDropArchivePage(listOf(drop("a")), nextBefore = "t1"))
                vm.loadArchive()
                assertNull(vm.state.value.archiveError)
                vm.loadMoreArchive()
                assertTrue(vm.state.value.archiveMoreFailed)
                assertEquals(
                    listOf("a"),
                    vm.state.value.archive
                        .map { it.id },
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun developingFromTheArchiveMarksItThereAndLoadsReactions() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                service.archivePages[null] = Result.success(SharedDropArchivePage(listOf(drop("old")), nextBefore = null))
                service.developResult = Result.success(DropDevelopResultDto("shared", "old", "developed", developedAt = past))
                val reactions = FakeReactions()
                reactions.loadResult = Result.success(ReactionsState(isOwner = false, mine = "👏"))
                val vm = viewModel(service, reactions = reactions)
                vm.refresh()
                vm.loadArchive()
                vm.develop(
                    vm.state.value.archive
                        .single(),
                )
                val developed =
                    vm.state.value.archive
                        .single()
                assertEquals(ClickDropDevelopState.Developed, developed.state(now))
                assertEquals("👏", developed.reactions?.mine)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun aLiveDropsHintRefreshesTheStrip() =
        runTest {
            try {
                val service = FakeSharedDropsService()
                val live = MutableSharedFlow<LiveBatch>(extraBufferCapacity = 4)
                val vm = viewModel(service, live = live)
                vm.refresh()
                assertTrue(
                    vm.state.value.drops
                        .isEmpty(),
                )
                service.stripResult = Result.success(listOf(drop("new")))
                live.emit(LiveBatch(setOf(LiveKind.NUDGES)))
                assertTrue(
                    vm.state.value.drops
                        .isEmpty(),
                )
                live.emit(LiveBatch(setOf(LiveKind.DROPS)))
                assertEquals(
                    listOf("new"),
                    vm.state.value.drops
                        .map { it.id },
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun replyCopyAndWhoCanReply() {
        assertEquals("Reaction sent to Maya", sharedDropReplyNotice("Maya", isReaction = true, sent = true))
        assertEquals("Sent to Maya", sharedDropReplyNotice("Maya", isReaction = false, sent = true))
        assertEquals("Couldn't send. Try again.", sharedDropReplyNotice("Maya", isReaction = true, sent = false))
        assertTrue(drop("a", connection = "c1").canReply(now))
        assertFalse(drop("a", connection = "c1", reveal = future).canReply(now))
        assertFalse(drop("a", connection = "c1", mine = true).canReply(now))
        assertFalse(drop("a").canReply(now))
    }

    @Test
    fun replyPhotoKeysMatchTheStripCache() {
        assertEquals("shared-drop-me-a-original", sharedDropOriginalKey("me", "a"))
        assertEquals("shared-drop-me-a-preview", sharedDropPreviewKey("me", "a"))
        assertEquals(listOf("a", "b", "c"), listOf(drop("a"), drop("b")).appendingPage(listOf(drop("b"), drop("c"))).map { it.id })
    }
}
