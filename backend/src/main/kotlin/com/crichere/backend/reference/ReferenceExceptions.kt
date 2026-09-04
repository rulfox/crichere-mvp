package com.crichere.backend.reference

/**
 * `GET /reference/states/{state}/districts` (or the pre-District-retrofit `.../cities`) was
 * called with a path segment that cannot possibly be a state code -- see [ReferenceController]
 * for exactly what "cannot possibly" means and why this is a `404`, distinct from a
 * syntactically valid but unrecognised code (which returns an empty list, per this task's
 * brief).
 */
class MalformedStateCodeException : RuntimeException("Malformed state code")

/**
 * `GET /reference/districts/{district}/cities` was called with a path segment that isn't a
 * valid UUID, so it cannot possibly be a district id -- same "shape first, existence second"
 * split [MalformedStateCodeException] already established: a syntactically valid UUID that
 * doesn't match any district returns an empty list (200), not this.
 */
class MalformedDistrictIdException : RuntimeException("Malformed district id")
