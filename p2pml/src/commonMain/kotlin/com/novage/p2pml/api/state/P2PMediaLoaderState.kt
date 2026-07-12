package com.novage.p2pml.api.state

import com.novage.p2pml.api.errors.P2PMediaLoaderException

/**
 * Lifecycle status of a [com.novage.p2pml.P2PMediaLoader].
 *
 * A loader moves [IDLE] → [STARTING] → [ACTIVE], then terminally to either [FAILED] or [RELEASED].
 * Calling release before initialization moves [IDLE] directly to [RELEASED].
 * Once terminal, the instance is single-use and must be discarded.
 */
enum class P2PMediaLoaderStatus { IDLE, STARTING, ACTIVE, FAILED, RELEASED }

/**
 * Observable loader state, exposed as a latched `StateFlow` so late subscribers always see the
 * current value (including a terminal [P2PMediaLoaderStatus.FAILED]).
 *
 * When [status] is [P2PMediaLoaderStatus.FAILED], the core has stopped and the local proxy is gone —
 * the host should switch the player to the origin URL. The fatal [error] travels atomically with
 * the `FAILED` status, so there is no read race between observing the status and reading the cause.
 *
 * @property status current lifecycle status.
 * @property error the fatal cause; non-null only when [status] is [P2PMediaLoaderStatus.FAILED].
 */
data class P2PMediaLoaderState(val status: P2PMediaLoaderStatus, val error: P2PMediaLoaderException? = null)
