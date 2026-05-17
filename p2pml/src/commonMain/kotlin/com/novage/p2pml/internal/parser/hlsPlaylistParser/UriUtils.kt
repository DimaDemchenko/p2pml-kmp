package com.novage.p2pml.internal.parser.hlsPlaylistParser

// --- Constants used for URI indices ---
private const val INDEX_COUNT = 4
private const val SCHEME_COLON = 0
private const val PATH = 1
private const val QUERY = 2
private const val FRAGMENT = 3

private const val SCHEME_SLASH_OFFSET = 2
private const val AUTHORITY_OFFSET = 3

internal fun resolveAbsoluteUrl(baseUri: String?, referenceUri: String?): String {
    val base = baseUri ?: ""
    val reference = referenceUri ?: ""

    val refIndices = getUriIndices(reference)
    val baseIndices = getUriIndices(base)

    return when {
        refIndices[SCHEME_COLON] != -1 -> {
            val sb = StringBuilder(reference)
            removeDotSegments(sb, refIndices[PATH], refIndices[QUERY])
        }

        refIndices[FRAGMENT] == 0 -> {
            StringBuilder().append(base, 0, baseIndices[FRAGMENT]).append(reference).toString()
        }

        refIndices[QUERY] == 0 -> {
            StringBuilder().append(base, 0, baseIndices[QUERY]).append(reference).toString()
        }

        refIndices[PATH] != 0 -> {
            val baseLimit = baseIndices[SCHEME_COLON] + 1
            val sb = StringBuilder().append(base, 0, baseLimit).append(reference)
            removeDotSegments(sb, baseLimit + refIndices[PATH], baseLimit + refIndices[QUERY])
        }

        reference.getOrNull(refIndices[PATH]) == '/' -> {
            val sb = StringBuilder().append(base, 0, baseIndices[PATH]).append(reference)
            removeDotSegments(sb, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY])
        }

        else -> resolveRelativePath(base, reference, baseIndices, refIndices)
    }
}

private fun resolveRelativePath(base: String, reference: String, baseIndices: IntArray, refIndices: IntArray): String {
    val isAuthoritySameAsQuery = baseIndices[PATH] == baseIndices[QUERY]
    val hasAuthoritySpace = baseIndices[SCHEME_COLON] + SCHEME_SLASH_OFFSET < baseIndices[PATH]

    return if (hasAuthoritySpace && isAuthoritySameAsQuery) {
        val sb = StringBuilder().append(base, 0, baseIndices[PATH]).append('/').append(reference)
        removeDotSegments(sb, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY] + 1)
    } else {
        val lastSlashIndex = base.lastIndexOf('/', baseIndices[QUERY] - 1)
        val baseLimit = if (lastSlashIndex == -1) baseIndices[PATH] else lastSlashIndex + 1
        val sb = StringBuilder().append(base, 0, baseLimit).append(reference)
        removeDotSegments(sb, baseIndices[PATH], baseLimit + refIndices[QUERY])
    }
}

internal fun removeDotSegments(sb: StringBuilder, offset: Int, limit: Int): String {
    var off = offset
    var lim = limit
    if (off >= lim) return sb.toString()
    if (sb[off] == '/') off++

    var segmentStart = off
    var i = off
    while (i <= lim) {
        val nextSegmentStart = when {
            i == lim -> i

            sb[i] == '/' -> i + 1

            else -> {
                i++
                continue
            }
        }
        if (i == segmentStart + 1 && sb[segmentStart] == '.') {
            sb.deleteRange(segmentStart, nextSegmentStart)
            lim -= nextSegmentStart - segmentStart
            i = segmentStart
        } else if (i == segmentStart + 2 && sb[segmentStart] == '.' && sb[segmentStart + 1] == '.') {
            val prevSegmentStart = sb.lastIndexOf("/", segmentStart - 2) + 1
            val removeFrom = if (prevSegmentStart > off) prevSegmentStart else off
            sb.deleteRange(removeFrom, nextSegmentStart)
            lim -= nextSegmentStart - removeFrom
            segmentStart = prevSegmentStart
            i = prevSegmentStart
        } else {
            i++
            segmentStart = i
        }
    }
    return sb.toString()
}

internal fun getUriIndices(uriString: String): IntArray {
    if (uriString.isEmpty()) {
        return IntArray(INDEX_COUNT).apply { this[SCHEME_COLON] = -1 }
    }

    val length = uriString.length
    val fragmentIndex = findFragmentIndex(uriString, length)
    val queryIndex = findQueryIndex(uriString, fragmentIndex)
    val schemeIndex = findSchemeIndex(uriString, queryIndex)
    val pathIndex = calculatePathIndex(uriString, schemeIndex, queryIndex)

    return IntArray(INDEX_COUNT).apply {
        this[SCHEME_COLON] = schemeIndex
        this[PATH] = pathIndex
        this[QUERY] = queryIndex
        this[FRAGMENT] = fragmentIndex
    }
}

private fun findFragmentIndex(uri: String, length: Int): Int {
    val index = uri.indexOf('#')
    return if (index == -1) length else index
}

private fun findQueryIndex(uri: String, fragmentIndex: Int): Int {
    val index = uri.indexOf('?')
    return if (index == -1 || index > fragmentIndex) fragmentIndex else index
}

private fun findSchemeIndex(uri: String, queryIndex: Int): Int {
    val firstSlash = uri.indexOf('/')
    val schemeLimit = if (firstSlash == -1 || firstSlash > queryIndex) queryIndex else firstSlash
    val schemeIndex = uri.indexOf(':')
    return if (schemeIndex > schemeLimit) -1 else schemeIndex
}

private fun calculatePathIndex(uri: String, schemeIndex: Int, queryIndex: Int): Int {
    val hasAuthority = schemeIndex != -1 &&
        schemeIndex + SCHEME_SLASH_OFFSET < queryIndex &&
        uri[schemeIndex + 1] == '/' &&
        uri[schemeIndex + 2] == '/'

    return if (hasAuthority) {
        val idx = uri.indexOf('/', schemeIndex + AUTHORITY_OFFSET)
        if (idx == -1 || idx > queryIndex) queryIndex else idx
    } else {
        schemeIndex + 1
    }
}
