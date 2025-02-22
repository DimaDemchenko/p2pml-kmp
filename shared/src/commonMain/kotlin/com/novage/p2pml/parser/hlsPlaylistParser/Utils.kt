package com.novage.p2pml.parser.hlsPlaylistParser

fun isLinebreak(c: Int): Boolean = c == '\n'.code || c == '\r'.code

fun skipIgnorableWhitespace(reader: Reader, skipLinebreaks: Boolean, c: Int): Int {
    var ch = c
    while (ch != -1 && ch.toChar().isWhitespace() && (skipLinebreaks || !isLinebreak(ch))) {
        ch = reader.read()
    }
    return ch
}

fun splitString(value: String, regex: String): List<String> =
    // TODO: Check if this is the correct way to split a string
    value.split(regex, ignoreCase = false, limit = 0)
