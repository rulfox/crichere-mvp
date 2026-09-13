package com.crichere.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

/** [EnableScheduling] backs [com.crichere.backend.auction.AuctionBroadcastService]'s SSE heartbeat. */
@SpringBootApplication
@EnableScheduling
class BackendApplication {

	/**
	 * The application's source of "now".
	 *
	 * Token issuance, expiry checks and revocation timestamps all depend on the current
	 * instant, and a service that calls `Instant.now()` directly cannot be tested for
	 * time-dependent behaviour -- "reject an expired token" would mean sleeping. Injecting a
	 * [Clock] makes that a fixed-clock assertion instead.
	 *
	 * UTC explicitly: every timestamp this app persists is a `TIMESTAMPTZ`, and pinning the
	 * clock's zone keeps behaviour identical regardless of the host machine's timezone.
	 */
	@Bean
	fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
	runApplication<BackendApplication>(*args)
}
