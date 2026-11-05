package app.lockin.core

/** Snapshot of the lock: whether it is on, how many screens were blocked, and the last quote shown. */
data class LockState(val enabled: Boolean, val blockCount: Int, val lastQuote: Int)
