package com.novage.p2pml.api.models

data class PlaylistSnapshot(val mediaSequence: Long, val hasEndTag: Boolean, val segmentDurations: List<Double>)
