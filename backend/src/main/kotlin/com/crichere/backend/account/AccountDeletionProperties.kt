package com.crichere.backend.account

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Account deletion (docs/PHASE16.md). */
@ConfigurationProperties(prefix = "crichere.account-deletion")
data class AccountDeletionProperties(
    /** How long a requested deletion waits before the purge; signing in again during it cancels the deletion. */
    val gracePeriod: Duration = Duration.ofDays(14),
    /** Turns the scheduled purge off (tests drive [AccountPurgeService] directly). */
    val purgeEnabled: Boolean = true,
    /** Spring cron for the purge job, in [purgeZone]. Default: daily at 03:30. */
    val purgeCron: String = "0 30 3 * * *",
    val purgeZone: String = "Asia/Kolkata",
)

@Configuration
@EnableConfigurationProperties(AccountDeletionProperties::class)
class AccountDeletionConfiguration
