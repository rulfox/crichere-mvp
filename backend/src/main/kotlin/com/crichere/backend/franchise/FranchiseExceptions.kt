package com.crichere.backend.franchise

/** No franchise row matches the given id under the given league -- also thrown (not a different error) when the id is real but belongs to a *different* league. */
class LeagueFranchiseNotFoundException : RuntimeException("Franchise not found")

/** An approve/dismiss was attempted on a franchise row with no pending leave request. */
class NoLeaveRequestPendingException : RuntimeException("No leave request is pending for this franchise")

/** The caller is neither the league's organizer nor this franchise's own owner -- checked on logo-upload presign and self-scoped leave request. */
sealed class FranchiseAuthorizationException(message: String) : RuntimeException(message) {
    class NotFranchiseOwnerException : FranchiseAuthorizationException("You are not this franchise's owner")
}

typealias NotFranchiseOwnerException = FranchiseAuthorizationException.NotFranchiseOwnerException
