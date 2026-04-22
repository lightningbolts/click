package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GroupCliqueDetails
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.syntheticConnectionForGroupClique
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalSearchResultsTest {

    @Test
    fun isEmpty_trueWhenNoItems() {
        assertTrue(GlobalSearchResults().isEmpty)
    }

    @Test
    fun visible_whenAllCategoriesSelected_returnsAllSorted() {
        val a = SearchResult.ActiveConnection(details = stubDetails("c1", "Alice"))
        val b = SearchResult.ArchivedConnection(details = stubDetails("c2", "Bob"))
        val results = GlobalSearchResults(items = listOf(b, a))
        val visible = results.visible(SearchResultCategory.entries.toSet())
        assertEquals(2, visible.size)
        // ArchivedConnection (typeRank 4) sorts after ActiveConnection (typeRank 3) at equal sortKey tie
        assertTrue(visible[0] is SearchResult.ActiveConnection)
        assertTrue(visible[1] is SearchResult.ArchivedConnection)
    }

    @Test
    fun visible_onlyCliques_excludesDirectChats() {
        val direct = SearchResult.ActiveConnection(details = stubDetails("c1", "Ann"))
        val clique = SearchResult.Clique(details = stubDetails("g1", "Study Group", isGroup = true))
        val results = GlobalSearchResults(items = listOf(direct, clique))
        val visible = results.visible(setOf(SearchResultCategory.Cliques))
        assertEquals(1, visible.size)
        assertTrue(visible.single() is SearchResult.Clique)
    }

    @Test
    fun visible_onlyNearby_includesLocationBucketAndMemoryContext() {
        val loc = SearchResult.LocationBucket(
            LocationSearchResult(location = "Campus", connectionCount = 1, connectionIds = listOf("c1")),
        )
        val mem = SearchResult.MemoryContextMatch(
            details = stubDetails("c1", "Sam"),
            matchLabel = "Context",
            isArchivedChannel = false,
        )
        val name = SearchResult.ActiveConnection(details = stubDetails("c2", "Pat"))
        val results = GlobalSearchResults(items = listOf(name, loc, mem))
        val visible = results.visible(setOf(SearchResultCategory.Nearby))
        assertEquals(2, visible.size)
        assertTrue(visible.any { it is SearchResult.LocationBucket })
        assertTrue(visible.any { it is SearchResult.MemoryContextMatch })
    }
}

private fun stubDetails(
    id: String,
    title: String,
    isGroup: Boolean = false,
): ChatWithDetails {
    val viewer = "viewer"
    val peer = "peer-$id"
    val conn = if (isGroup) {
        syntheticConnectionForGroupClique(groupId = id, memberUserIds = listOf(viewer, peer, "p3"))
    } else {
        Connection(
            id = id,
            created = 1L,
            expiry = Long.MAX_VALUE,
            user_ids = listOf(viewer, peer),
            status = "kept",
        )
    }
    val gc = if (isGroup) {
        GroupCliqueDetails(
            groupId = id,
            name = title,
            createdByUserId = viewer,
            keyAnchorUserId = peer,
            memberUserIds = conn.user_ids,
        )
    } else {
        null
    }
    return ChatWithDetails(
        chat = Chat(
            id = "chat-$id",
            connectionId = if (isGroup) null else id,
            groupId = if (isGroup) id else null,
            messages = emptyList(),
        ),
        connection = conn,
        otherUser = User(id = peer, name = if (isGroup) "Member" else title),
        lastMessage = null,
        unreadCount = 0,
        groupClique = gc,
        groupMemberUsers = if (isGroup) {
            listOf(User(id = peer, name = "Member"))
        } else {
            emptyList()
        },
    )
}
