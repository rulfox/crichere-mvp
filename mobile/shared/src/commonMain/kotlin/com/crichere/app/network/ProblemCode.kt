package com.crichere.app.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * The backend's machine-readable error `code` (e.g. `CAPACITY_FULL`) from an RFC 7807 problem
 * body -- see the backend's `ProblemDetails`. `null` if the body is missing or isn't a problem.
 */
suspend fun HttpResponse.problemCode(): String? = runCatching {
    lenientJson.parseToJsonElement(bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content
}.getOrNull()
