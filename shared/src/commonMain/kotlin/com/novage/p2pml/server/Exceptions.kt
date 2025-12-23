package com.novage.p2pml.server

internal class SegmentAbortedException(message: String) : Exception(message)
internal class SegmentReplacedException(message: String) : Exception(message)
internal class TooManyRetriesException(message: String) : Exception(message)