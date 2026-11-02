package compose.project.click.click.viewmodel

import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropAudience
import compose.project.click.click.collaboration.SharedDropUser
import compose.project.click.click.data.api.DropDevelopResultDto
import compose.project.click.click.data.api.SharedDropCreateBody
import compose.project.click.click.data.api.SharedDropPostException
import compose.project.click.click.data.api.SharedDropsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
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

    override suspend fun strip() = stripResult

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
    ) = SharedDrop(
        id = id,
        user = SharedDropUser(user, user),
        isMine = mine,
        revealAtIso = reveal,
        developedAtIso = developed,
        previewUrl = preview,
        originalUrl = original,
    )

    private fun TestScope.viewModel(
        service: FakeSharedDropsService,
        disk: FakeDisk = FakeDisk(),
        user: () -> String? = { "me" },
        preview: ByteArray? = byteArrayOf(7),
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
        )
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
}
