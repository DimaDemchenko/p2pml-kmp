package com.novage.p2pml.domain.models

import kotlinx.serialization.Serializable

@Serializable data class ByteRange(val start: Long, val end: Long)