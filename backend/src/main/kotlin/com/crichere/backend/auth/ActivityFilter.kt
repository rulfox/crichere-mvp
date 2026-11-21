package com.crichere.backend.auth

import com.crichere.backend.admin.ActivityTracker
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Marks today as an active day for the signed-in user once their request has succeeded
 * (docs/PHASE20.md Step 6, decided 2026-11-21: any successful signed-in app request counts).
 * Runs after [SuspensionFilter], so a refused or failed request never counts. Only the day is
 * kept, never the request itself.
 */
class ActivityFilter(private val tracker: ActivityTracker) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        // Read before the chain: the context is cleared by the time a long request returns.
        val userId = SecurityContextHolder.getContext().authentication?.principal as? UUID
        filterChain.doFilter(request, response)
        if (userId != null && response.status < 400) {
            runCatching { tracker.markActive(userId) }
        }
    }
}
