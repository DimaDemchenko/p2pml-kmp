package com.novage.p2pml.parser.hlsPlaylistParser

// --- Constants used for URI indices ---
private const val INDEX_COUNT = 4
private const val SCHEME_COLON = 0
private const val PATH = 1
private const val QUERY = 2
private const val FRAGMENT = 3

/**
 * Performs relative resolution of a [referenceUri] with respect to a [baseUri] following RFC-3986.
 *
 * @param baseUri The base URI, or null which is treated as the empty string.
 * @param referenceUri The reference URI to resolve, or null which is treated as the empty string.
 * @return The resolved URI.
 */
internal fun resolve(baseUri: String?, referenceUri: String?): String {
    val base = baseUri ?: ""
    val reference = referenceUri ?: ""

    val refIndices = getUriIndices(reference)
    if (refIndices[SCHEME_COLON] != -1) {
        // The reference is absolute. The target URI is the reference.
        val sb = StringBuilder(reference)
        return removeDotSegments(sb, refIndices[PATH], refIndices[QUERY])
    }
    val baseIndices = getUriIndices(base)
    if (refIndices[FRAGMENT] == 0) {
        // Reference is empty or only a fragment: base URI without its fragment plus the reference.
        return StringBuilder().append(base, 0, baseIndices[FRAGMENT]).append(reference).toString()
    }
    if (refIndices[QUERY] == 0) {
        // Reference starts with a query.
        return StringBuilder().append(base, 0, baseIndices[QUERY]).append(reference).toString()
    }
    if (refIndices[PATH] != 0) {
        // The reference has an authority.
        val baseLimit = baseIndices[SCHEME_COLON] + 1
        val sb = StringBuilder()
        sb.append(base, 0, baseLimit).append(reference)
        return removeDotSegments(sb, baseLimit + refIndices[PATH], baseLimit + refIndices[QUERY])
    }
    if (reference[refIndices[PATH]] == '/') {
        // The reference path is rooted.
        val sb = StringBuilder()
        sb.append(base, 0, baseIndices[PATH]).append(reference)
        return removeDotSegments(sb, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY])
    }
    // Otherwise, append the reference after removing the last segment of the base's path.
    return if (
        baseIndices[SCHEME_COLON] + 2 < baseIndices[PATH] && baseIndices[PATH] == baseIndices[QUERY]
    ) {
        val sb = StringBuilder()
        sb.append(base, 0, baseIndices[PATH]).append('/').append(reference)
        removeDotSegments(sb, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY] + 1)
    } else {
        val lastSlashIndex = base.lastIndexOf('/', baseIndices[QUERY] - 1)
        val baseLimit = if (lastSlashIndex == -1) baseIndices[PATH] else lastSlashIndex + 1
        val sb = StringBuilder()
        sb.append(base, 0, baseLimit).append(reference)
        removeDotSegments(sb, baseIndices[PATH], baseLimit + refIndices[QUERY])
    }
}

/** Returns true if [uri] is absolute (i.e. starts with a scheme), false otherwise. */
internal fun isAbsolute(uri: String?): Boolean {
    return uri != null && getUriIndices(uri)[SCHEME_COLON] != -1
}

/**
 * Removes the specified [queryParameterName] from the query component of [uri].
 *
 * @param uri The URI as a string.
 * @param queryParameterName The name of the query parameter to remove.
 * @return A new URI string without the specified query parameter.
 */
internal fun removeQueryParameter(uri: String, queryParameterName: String): String {
    val qIndex = uri.indexOf('?')
    if (qIndex == -1) return uri

    val fragmentIndex = uri.indexOf('#', qIndex)
    val base = if (fragmentIndex == -1) uri.substring(0, qIndex) else uri.substring(0, qIndex)
    val query =
        if (fragmentIndex == -1) uri.substring(qIndex + 1)
        else uri.substring(qIndex + 1, fragmentIndex)
    val fragment = if (fragmentIndex == -1) "" else uri.substring(fragmentIndex)

    val params = query.split('&').filter { it.isNotEmpty() }
    val filtered =
        params.filter { param ->
            val eqIndex = param.indexOf('=')
            val key = if (eqIndex != -1) param.substring(0, eqIndex) else param
            key != queryParameterName
        }
    return if (filtered.isEmpty()) {
        base + fragment
    } else {
        base + "?" + filtered.joinToString("&") + fragment
    }
}

/**
 * Removes dot-segments ("." and "..") from the path of the URI contained in [sb].
 *
 * @param sb A StringBuilder containing the URI.
 * @param offset The index at which the path starts.
 * @param limit The index (exclusive) at which the path ends.
 * @return The StringBuilder’s string after removing dot segments.
 */
internal fun removeDotSegments(sb: StringBuilder, offset: Int, limit: Int): String {
    var off = offset
    var lim = limit
    if (off >= lim) {
        return sb.toString()
    }
    if (sb[off] == '/') {
        // Retain the initial slash.
        off++
    }
    var segmentStart = off
    var i = off
    while (i <= lim) {
        val nextSegmentStart =
            when {
                i == lim -> i
                sb[i] == '/' -> i + 1
                else -> {
                    i++
                    continue
                }
            }
        if (i == segmentStart + 1 && sb[segmentStart] == '.') {
            // Remove "./" segment.

            sb.deleteRange(segmentStart, nextSegmentStart)
            lim -= nextSegmentStart - segmentStart
            i = segmentStart
        } else if (
            i == segmentStart + 2 && sb[segmentStart] == '.' && sb[segmentStart + 1] == '.'
        ) {
            // Remove "../" segment.
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

/**
 * Calculates indices of the constituent components of a URI.
 *
 * Returns an [IntArray] of size 4 where:
 * - index 0: index of ':' after the scheme (or -1 if not found),
 * - index 1: index where the path starts,
 * - index 2: index where the query starts (or equals fragment index if none),
 * - index 3: index where the fragment starts (or equals the length of the URI if none).
 */
internal fun getUriIndices(uriString: String): IntArray {
    val indices = IntArray(INDEX_COUNT)
    if (uriString.isEmpty()) {
        indices[SCHEME_COLON] = -1
        return indices
    }
    val length = uriString.length
    var fragmentIndex = uriString.indexOf('#')
    if (fragmentIndex == -1) {
        fragmentIndex = length
    }
    var queryIndex = uriString.indexOf('?')
    if (queryIndex == -1 || queryIndex > fragmentIndex) {
        queryIndex = fragmentIndex
    }
    var schemeIndexLimit = uriString.indexOf('/')
    if (schemeIndexLimit == -1 || schemeIndexLimit > queryIndex) {
        schemeIndexLimit = queryIndex
    }
    var schemeIndex = uriString.indexOf(':')
    if (schemeIndex > schemeIndexLimit) {
        schemeIndex = -1
    }
    val hasAuthority =
        schemeIndex != -1 &&
            schemeIndex + 2 < queryIndex &&
            uriString[schemeIndex + 1] == '/' &&
            uriString[schemeIndex + 2] == '/'
    val pathIndex =
        if (hasAuthority) {
            val idx = uriString.indexOf('/', schemeIndex + 3)
            if (idx == -1 || idx > queryIndex) queryIndex else idx
        } else {
            schemeIndex + 1
        }
    indices[SCHEME_COLON] = schemeIndex
    indices[PATH] = pathIndex
    indices[QUERY] = queryIndex
    indices[FRAGMENT] = fragmentIndex
    return indices
}

/**
 * Returns the relative path from [baseUri] to [targetUri]. If the two URIs have different schemes
 * or authorities, [targetUri] is returned unchanged.
 *
 * This implementation splits the path into segments (delimited by '/') and then computes the common
 * prefix.
 */
internal fun getRelativePath(baseUri: String, targetUri: String): String {
    // For opaque URIs or if schemes/authorities differ, return targetUri.
    val baseIndices = getUriIndices(baseUri)
    val targetIndices = getUriIndices(targetUri)
    val baseScheme =
        if (baseIndices[SCHEME_COLON] != -1) baseUri.substring(0, baseIndices[SCHEME_COLON]) else ""
    val targetScheme =
        if (targetIndices[SCHEME_COLON] != -1) targetUri.substring(0, targetIndices[SCHEME_COLON])
        else ""
    if (!baseScheme.equals(targetScheme, ignoreCase = true)) return targetUri

    fun getAuthority(uri: String, indices: IntArray): String {
        return if (
            indices[SCHEME_COLON] != -1 &&
                uri.length > indices[SCHEME_COLON] + 2 &&
                uri[indices[SCHEME_COLON] + 1] == '/' &&
                uri[indices[SCHEME_COLON] + 2] == '/'
        ) {
            uri.substring(indices[SCHEME_COLON] + 3, indices[PATH])
        } else ""
    }
    val baseAuthority = getAuthority(baseUri, baseIndices)
    val targetAuthority = getAuthority(targetUri, targetIndices)
    if (!baseAuthority.equals(targetAuthority, ignoreCase = true)) return targetUri

    val basePathSegments = getPathSegments(baseUri)
    val targetPathSegments = getPathSegments(targetUri)

    val commonPrefixCount =
        basePathSegments.zip(targetPathSegments).takeWhile { it.first == it.second }.count()

    val relativePath = StringBuilder()
    for (i in commonPrefixCount until basePathSegments.size) {
        relativePath.append("../")
    }
    for ((index, segment) in targetPathSegments.withIndex()) {
        if (index >= commonPrefixCount) {
            relativePath.append(segment)
            if (index < targetPathSegments.size - 1) {
                relativePath.append("/")
            }
        }
    }
    return relativePath.toString()
}

/** Helper: Extracts the path segments from the URI. */
internal fun getPathSegments(uri: String): List<String> {
    val indices = getUriIndices(uri)
    val path = uri.substring(indices[PATH], indices[QUERY])
    return path.split('/').filter { it.isNotEmpty() }
}
