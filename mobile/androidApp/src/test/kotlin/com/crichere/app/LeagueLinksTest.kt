package com.crichere.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeagueLinksTest {

    @Test
    fun `custom scheme link yields the league id`() {
        assertEquals("abc", leagueIdFromLink("crichere", "leagues", listOf("abc")))
    }

    @Test
    fun `https watch link on crichere com yields the league id`() {
        assertEquals("abc", leagueIdFromLink("https", "crichere.com", listOf("leagues", "abc")))
    }

    @Test
    fun `other hosts, schemes and paths are ignored`() {
        assertNull(leagueIdFromLink("https", "evil.example", listOf("leagues", "abc")))
        assertNull(leagueIdFromLink("http", "crichere.com", listOf("leagues", "abc")))
        assertNull(leagueIdFromLink("https", "crichere.com", listOf("about")))
        assertNull(leagueIdFromLink("https", "crichere.com", listOf("leagues")))
        assertNull(leagueIdFromLink("crichere", "profile", listOf("abc")))
        assertNull(leagueIdFromLink(null, null, emptyList()))
    }

    @Test
    fun `blank id is ignored`() {
        assertNull(leagueIdFromLink("https", "crichere.com", listOf("leagues", " ")))
    }
}
