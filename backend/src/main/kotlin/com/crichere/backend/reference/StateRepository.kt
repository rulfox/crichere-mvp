package com.crichere.backend.reference

import org.springframework.data.jpa.repository.JpaRepository

/** Spring Data repository for [StateEntity]. */
interface StateRepository : JpaRepository<StateEntity, String>
