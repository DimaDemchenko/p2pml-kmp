package com.novage.p2pml.internal.server.exceptions

internal class SegmentAbortedException(message: String) : Exception(message)
internal class SegmentReplacedException(message: String) : Exception(message)
internal class TooManyRetriesException(message: String) : Exception(message)
internal class SegmentProcessingException(message: String) : Exception(message)
