package com.crichere.backend.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import java.net.URI
import java.time.Instant

/**
 * Builds every error body this API returns, so they all look the same.
 *
 * There are two entirely separate places errors surface -- `@RestControllerAdvice` for
 * anything a controller throws, and Spring Security's `AuthenticationEntryPoint` /
 * `AccessDeniedHandler` for rejections that happen in the filter chain, before a controller
 * is ever chosen. Without a shared builder those two drift apart and clients end up parsing
 * two different error formats depending on how far into the stack the request got.
 *
 * ## The fields, and what is deliberately absent
 *
 * RFC 7807 standard members:
 *  - `type` — a stable, dereferenceable-looking URI naming the error class.
 *  - `title` — short, human-readable, the same for every occurrence of a `type`.
 *  - `status` — the HTTP status, repeated in the body as the RFC intends.
 *  - `detail` — human-readable and *always a fixed string chosen by us*. Never an exception
 *    message, never a database message. This is the field that leaks stack frames, SQL, and
 *    internal class names in a careless implementation.
 *  - `instance` — the request path, so a report can be tied to an endpoint. The path is
 *    already known to the caller, so it reveals nothing.
 *
 * Extensions:
 *  - `code` — a stable machine-readable identifier the mobile app can branch on without
 *    string-matching the human text.
 *  - `timestamp` — when the failure happened, for correlating with server logs.
 *  - `errors` — field-name to message map, on validation failures only.
 */
object ProblemDetails {

    private const val TYPE_BASE = "https://api.crichere.app/problems/"

    /**
     * @param slug the trailing segment of the `type` URI, e.g. `invalid-credentials`.
     * @param code the stable machine-readable code, e.g. `INVALID_CREDENTIALS`.
     * @param detail a fixed, safe, human-readable sentence. Never derived from an exception.
     */
    fun of(
        status: HttpStatus,
        slug: String,
        title: String,
        code: String,
        detail: String,
        instance: String? = null,
        extensions: Map<String, Any> = emptyMap(),
    ): ProblemDetail =
        ProblemDetail.forStatus(status).apply {
            type = URI.create(TYPE_BASE + slug)
            this.title = title
            this.detail = detail
            // A request path that will not parse as a URI is not worth failing the error
            // response over -- the field is diagnostic, not load-bearing.
            instance?.let { path -> runCatching { URI.create(path) }.getOrNull()?.let { this.instance = it } }
            setProperty("code", code)
            setProperty("timestamp", Instant.now().toString())
            extensions.forEach { (key, value) -> setProperty(key, value) }
        }
}
