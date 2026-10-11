package com.crichere.backend.account

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Runs [AccountPurgeService] on [AccountDeletionProperties.purgeCron] (docs/PHASE16.md). */
@Component
class AccountPurgeJob(
    private val accountPurgeService: AccountPurgeService,
    private val properties: AccountDeletionProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${crichere.account-deletion.purge-cron:0 30 3 * * *}", zone = "\${crichere.account-deletion.purge-zone:Asia/Kolkata}")
    fun run() {
        if (!properties.purgeEnabled) return
        val outcomes = accountPurgeService.purgeDue()
        if (outcomes.isNotEmpty()) {
            log.info("Account purge run: {}", outcomes.values.groupingBy { it }.eachCount())
        }
    }
}
