package com.crichere.backend.common

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

/**
 * Deletes objects from the media bucket -- an account purge erasing a profile photo and payment
 * screenshots (docs/PHASE16.md). A `fun interface` so tests can record keys instead of calling AWS.
 */
fun interface ObjectDeleter {
    /**
     * Deletes each key. Deleting a key that doesn't exist succeeds (S3 semantics), so a retried
     * purge is harmless.
     *
     * @throws Exception any key couldn't be deleted -- the purge then stops for that account and
     *   retries on the next run, so no personal data is left behind silently.
     */
    fun delete(keys: Collection<String>)
}

/**
 * The real [ObjectDeleter]. Needs `s3:DeleteObject` on the `users/` and `leagues/{id}/payments/`
 * prefixes for the backend's IAM user. No bucket configured (dev/CI/local) means there is nothing to delete, so
 * it logs and returns -- same fail-soft posture as [S3PaymentScreenshotUrlSigner].
 */
@Component
class S3ObjectDeleter(
    private val properties: AwsProperties,
    private val credentialsProvider: AwsCredentialsProvider,
) : ObjectDeleter {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Built on first use, so missing AWS setup can't fail startup. */
    private val client: S3Client by lazy {
        S3Client.builder()
            .region(Region.of(properties.s3.region))
            .credentialsProvider(credentialsProvider)
            .build()
    }

    override fun delete(keys: Collection<String>) {
        val bucket = properties.s3.bucket
        if (bucket.isBlank() || properties.s3.region.isBlank()) {
            log.info("No media bucket configured; skipped deleting {} object(s)", keys.size)
            return
        }
        keys.forEach { key -> client.deleteObject { it.bucket(bucket).key(key) } }
    }
}
