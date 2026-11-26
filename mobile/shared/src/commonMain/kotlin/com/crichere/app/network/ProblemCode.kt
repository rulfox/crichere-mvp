package com.crichere.app.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The account is suspended (docs/PHASE18.md). */
const val ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED"

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * The backend's machine-readable error `code` (e.g. `CAPACITY_FULL`) from an RFC 7807 problem
 * body -- see the backend's `ProblemDetails`. `null` if the body is missing or isn't a problem.
 */
suspend fun HttpResponse.problemCode(): String? = runCatching {
    lenientJson.parseToJsonElement(bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content
}.getOrNull()

/** The app build is older than the backend's minimum (docs/PHASE23.md). */
const val APP_UPDATE_REQUIRED = "APP_UPDATE_REQUIRED"

/** A problem body's string fields (`code`, `updateUrl`, …); empty if the body isn't a JSON object. */
suspend fun HttpResponse.problemStrings(): Map<String, String> = runCatching {
    lenientJson.parseToJsonElement(bodyAsText()).jsonObject
        .mapNotNull { (key, value) -> (value as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content } }
        .toMap()
}.getOrDefault(emptyMap())
