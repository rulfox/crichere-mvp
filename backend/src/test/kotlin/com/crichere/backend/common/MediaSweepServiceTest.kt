package com.crichere.backend.common

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaSweepServiceTest {

    private val now: Instant = Instant.parse("2026-10-12T22:30:00Z")
    private val old = now.minus(Duration.ofDays(3))
    private val host = "https://crichere-media-prod.s3.ap-south-1.amazonaws.com/"
    private val user = UUID.randomUUID()
    private val league = UUID.randomUUID()

    private val deleted = mutableListOf<String>()
    private val jdbc = mockk<JdbcTemplate>()

    private fun service(listing: (String) -> List<StoredObject>, maxDeletes: Int = 1000) = MediaSweepService(
        lister = ObjectLister(listing),
        deleter = ObjectDeleter { deleted += it },
        jdbcTemplate = jdbc,
        properties = MediaSweepProperties(maxDeletesPerRun = maxDeletes),
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun referenced(vararg urls: String) {
        every { jdbc.queryForList(any<String>(), String::class.java) } returns urls.toList()
    }

    @Test
    fun `deletes old unreferenced photos and abandoned franchise logos, keeps everything else`() {
        val current = "users/$user/profile-${UUID.randomUUID()}.jpg"
        val replaced = "users/$user/profile-${UUID.randomUUID()}.jpg"
        val fresh = "users/$user/profile-${UUID.randomUUID()}.jpg"
        val abandonedLogo = "leagues/$league/franchise-logos/$user-${UUID.randomUUID()}.jpg"
        val leagueLogo = "leagues/$league/logo-${UUID.randomUUID()}.jpg"
        val payment = "leagues/$league/payments/$user.jpg"
        val unrelated = "leagues/$league/notes.txt"
        referenced("$host$current?v=abc", "${host}$leagueLogo?v=1", "https://elsewhere.example/x.jpg")

        val count = service({ prefix ->
            listOf(
                StoredObject(current, old),
                StoredObject(replaced, old),
                StoredObject(fresh, now.minus(Duration.ofHours(2))),
                StoredObject(abandonedLogo, old),
                StoredObject(leagueLogo, old),
                StoredObject(payment, old),
                StoredObject(unrelated, old),
            ).filter { it.key.startsWith(prefix) }
        }).sweep()

        assertEquals(setOf(replaced, abandonedLogo), deleted.toSet())
        assertEquals(2, count)
    }

    @Test
    fun `never deletes more than the per-run cap`() {
        referenced()
        val keys = List(5) { StoredObject("users/${UUID.randomUUID()}/profile-${UUID.randomUUID()}.jpg", old) }

        service({ prefix -> keys.filter { it.key.startsWith(prefix) } }, maxDeletes = 3).sweep()

        assertEquals(3, deleted.size)
    }

    @Test
    fun `a listing failure (no IAM grant yet) deletes nothing`() {
        referenced()

        val count = service({ throw IllegalStateException("AccessDenied") }).sweep()

        assertEquals(0, count)
        assertTrue(deleted.isEmpty())
    }
}
