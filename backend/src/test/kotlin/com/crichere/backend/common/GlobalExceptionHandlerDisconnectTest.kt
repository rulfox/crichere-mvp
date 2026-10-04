package com.crichere.backend.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import java.io.IOException

class GlobalExceptionHandlerDisconnectTest {

    private val handler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest("GET", "/api/v1/leagues/l1/auction/stream")

    @Test
    fun `a client that disconnects mid-stream is handled without producing an error body`() {
        // Returns Unit: nothing is written into the dead text/event-stream response.
        handler.handleClientDisconnected(AsyncRequestNotUsableException("disconnected client", IOException("broken pipe")), request)
    }

    @Test
    fun `other unexpected exceptions still become a 500 problem`() {
        val problem: ProblemDetail = handler.handleUnexpected(IllegalStateException("boom"), request)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.status)
    }
}
