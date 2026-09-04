package com.crichere.backend.reference

import com.crichere.backend.common.AbstractWebIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `/reference/states` and `/reference/states/{state}/cities` over real HTTP -- with no
 * `Authorization` header sent at all, proving `SecurityConfig`'s `permitAll()` on every path
 * under `/api/v1/reference/` actually covers this exact path shape (including the
 * path-variable one) rather than assuming Task 3's wiring is correct for it.
 */
class ReferenceIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `states are public, unauthenticated, and include the real seeded data`() {
        val result = mockMvc.perform(get("/api/v1/reference/states"))
            .andExpect(status().isOk)
            .andReturn()

        val states = readList(result.response.contentAsString)
        assertEquals(36, states.size, "28 states + 8 union territories, per V4__seed_states_cities.sql")
        assertTrue(states.any { it["code"] == "KA" && it["name"] == "Karnataka" })
    }

    @Test
    fun `cities for a real state code are public and include the real seeded data`() {
        val result = mockMvc.perform(get("/api/v1/reference/states/KA/cities"))
            .andExpect(status().isOk)
            .andReturn()

        assertTrue(readList(result.response.contentAsString).any { it["name"] == "Bengaluru" })
    }

    @Test
    fun `the state code lookup is case-insensitive`() {
        val result = mockMvc.perform(get("/api/v1/reference/states/ka/cities"))
            .andExpect(status().isOk)
            .andReturn()

        assertTrue(readList(result.response.contentAsString).any { it["name"] == "Bengaluru" })
    }

    @Test
    fun `a syntactically valid but unknown state code returns an empty list, not an error`() {
        mockMvc.perform(get("/api/v1/reference/states/ZZ/cities"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `a malformed state code returns a 404 problem detail`() {
        mockMvc.perform(get("/api/v1/reference/states/not-a-code/cities"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://api.crichere.app/problems/not-found"))
            .andExpect(jsonPath("$.title").value("Not found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.instance").value("/api/v1/reference/states/not-a-code/cities"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
    }

    @Test
    fun `a numeric state code is also treated as malformed`() {
        mockMvc.perform(get("/api/v1/reference/states/12/cities"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun readList(json: String): List<Map<String, Any?>> =
        objectMapper.readValue(json, List::class.java) as List<Map<String, Any?>>
}
