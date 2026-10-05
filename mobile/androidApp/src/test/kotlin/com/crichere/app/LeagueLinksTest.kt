package com.crichere.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeagueLinksTest {

    @Test
    fun `custom scheme link yields the league id`() {
        assertEquals(ID, leagueIdFromLink("crichere", "leagues", listOf(ID)))
    }

    @Test
    fun `https watch link on crichere com yields the league id`() {
        assertEquals(ID, leagueIdFromLink("https", "crichere.com", listOf("leagues", ID)))
    }

    @Test
    fun `other hosts, schemes and paths are ignored`() {
        assertNull(leagueIdFromLink("https", "evil.example", listOf("leagues", ID)))
        assertNull(leagueIdFromLink("http", "crichere.com", listOf("leagues", ID)))
        assertNull(leagueIdFromLink("https", "crichere.com", listOf("about")))
        assertNull(leagueIdFromLink("https", "crichere.com", listOf("leagues")))
        assertNull(leagueIdFromLink("crichere", "profile", listOf(ID)))
        assertNull(leagueIdFromLink(null, null, emptyList()))
    }

    @Test
    fun `blank id is ignored`() {
        assertNull(leagueIdFromLink("https", "crichere.com", listOf("leagues", " ")))
    }

    @Test
    fun `an id that is not a UUID is ignored -- it would otherwise land in an authenticated API path`() {
        assertNull(leagueIdFromLink("crichere", "leagues", listOf("../auth/logout")))
        assertNull(leagueIdFromLink("https", "crichere.com", listOf("leagues", "$ID/../../me/leagues")))
        assertNull(leagueIdFromLink("crichere", "leagues", listOf("abc")))
    }

    private companion object {
        const val ID = "9d277037-56a9-4480-87c1-d93c687e9621"
    }
}
