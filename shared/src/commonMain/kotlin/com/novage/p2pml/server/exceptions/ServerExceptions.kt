package com.novage.p2pml.server.exceptions

internal class SegmentAbortedException(message: String) : Exception(message)
internal class SegmentReplacedException(message: String) : Exception(message)
internal class TooManyRetriesException(message: String) : Exception(message)
