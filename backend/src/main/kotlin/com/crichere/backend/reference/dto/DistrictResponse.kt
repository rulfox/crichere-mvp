package com.crichere.backend.reference.dto

import java.util.UUID

/** One row of `GET /api/v1/reference/states/{state}/districts`. */
data class DistrictResponse(val id: UUID, val name: String)
