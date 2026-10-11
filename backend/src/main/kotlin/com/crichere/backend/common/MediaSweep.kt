package com.crichere.backend.common

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The daily sweep of uploads nothing points to (docs/SECURITY-AUDIT.md Finding 6). */
@ConfigurationProperties(prefix = "crichere.media-sweep")
data class MediaSweepProperties(
    /** Turns the scheduled run off (tests call [MediaSweepService] directly). */
    val enabled: Boolean = true,
    /** Spring cron, in [zone]. Default: daily at 04:00, after the account purge. */
    val cron: String = "0 0 4 * * *",
    val zone: String = "Asia/Kolkata",
    /** An upload younger than this is kept: its edit may still be open in the app. */
    val minAge: Duration = Duration.ofHours(24),
    /** At most this many deletes per run, so a bug can't empty the bucket in one go. */
    val maxDeletesPerRun: Int = 1000,
)

@Configuration
@EnableConfigurationProperties(MediaSweepProperties::class)
class MediaSweepConfiguration

/** One object in the media bucket. */
data class StoredObject(val key: String, val lastModified: Instant)

/** Lists the media bucket. A `fun interface` so tests can hand back a fixed listing. */
fun interface ObjectLister {
    /** Every object under [prefix]; empty when no bucket is configured. */
    fun list(prefix: String): List<StoredObject>
}

/**
 * The real [ObjectLister]: paginated `ListObjectsV2`. Needs `s3:ListBucket` on the bucket for the
 * backend's IAM user. No bucket configured (dev/CI/local) returns nothing.
 */
@Component
class S3ObjectLister(
    private val properties: AwsProperties,
    private val credentialsProvider: AwsCredentialsProvider,
) : ObjectLister {

    private val client: S3Client by lazy {
        S3Client.builder()
            .region(Region.of(properties.s3.region))
            .credentialsProvider(credentialsProvider)
            .build()
    }

    override fun list(prefix: String): List<StoredObject> {
        val bucket = properties.s3.bucket
        if (bucket.isBlank() || properties.s3.region.isBlank()) return emptyList()
        return client.listObjectsV2Paginator { it.bucket(bucket).prefix(prefix) }
            .contents()
            .map { StoredObject(it.key(), it.lastModified()) }
    }
}

/**
 * Deletes uploads that no record points to: photos and logos replaced by a newer upload (each upload
 * has its own key), edits that were never saved, and logos picked for a franchise claim that was
 * never sent.
 *
 * Only image keys the upload endpoints issue are candidates, never payment screenshots (the account
 * purge owns those). A candidate goes only when it is older than [MediaSweepProperties.minAge] and
 * no stored URL names it.
 */
@Service
class MediaSweepService(
    private val lister: ObjectLister,
    private val deleter: ObjectDeleter,
    private val jdbcTemplate: JdbcTemplate,
    private val properties: MediaSweepProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** @return how many objects were deleted. */
    fun sweep(): Int {
        val now = clock.instant()
        val objects = try {
            PREFIXES.flatMap { lister.list(it) }
        } catch (e: Exception) {
            // Typically AccessDenied until the IAM grant is in place. Nothing is deleted.
            log.warn("Media sweep skipped: couldn't list the bucket ({})", e.javaClass.simpleName)
            return 0
        }
        if (objects.isEmpty()) return 0

        val referenced = referencedKeys()
        val cutoff = now.minus(properties.minAge)
        val orphans = objects
            .filter { CANDIDATE.matches(it.key) && it.lastModified.isBefore(cutoff) && it.key !in referenced }
            .map { it.key }
            .take(properties.maxDeletesPerRun)
        if (orphans.isEmpty()) return 0

        return try {
            deleter.delete(orphans)
            log.info("Media sweep deleted {} unreferenced upload(s)", orphans.size)
            orphans.size
        } catch (e: Exception) {
            log.warn("Media sweep couldn't delete ({}); will retry tomorrow", e.javaClass.simpleName)
            0
        }
    }

    /** Every object key a stored image URL points at: the part after the host, without the `?v=` query. */
    private fun referencedKeys(): Set<String> =
        jdbcTemplate.queryForList(REFERENCED_URLS, String::class.java)
            .mapNotNull { url -> url?.substringAfter(".amazonaws.com/", "")?.substringBefore('?')?.ifBlank { null } }
            .toSet()

    private companion object {
        val PREFIXES = listOf("users/", "leagues/")
        const val UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        val CANDIDATE = Regex(
            "^(users/$UUID/profile(-$UUID)?|leagues/$UUID/(logo|banner)(-$UUID)?|leagues/$UUID/franchise-logos/$UUID-$UUID)\\.jpg$",
        )
        const val REFERENCED_URLS = """
            select photo_url from profiles where photo_url is not null
            union select logo_url from leagues where logo_url is not null
            union select banner_url from leagues where banner_url is not null
            union select logo_url from league_franchises where logo_url is not null
        """
    }
}

/** Runs [MediaSweepService] on [MediaSweepProperties.cron]. */
@Component
class MediaSweepJob(
    private val service: MediaSweepService,
    private val properties: MediaSweepProperties,
) {
    @Scheduled(cron = "\${crichere.media-sweep.cron:0 0 4 * * *}", zone = "\${crichere.media-sweep.zone:Asia/Kolkata}")
    fun run() {
        if (properties.enabled) service.sweep()
    }
}
