package com.crichere.backend.reference

/**
 * `GET /reference/states/{state}/cities` was called with a path segment that cannot possibly
 * be a state code -- see [ReferenceController] for exactly what "cannot possibly" means and
 * why this is a `404`, distinct from a syntactically valid but unrecognised code (which
 * returns an empty list, per this task's brief).
 */
class MalformedStateCodeException : RuntimeException("Malformed state code")
