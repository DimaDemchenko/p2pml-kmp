package com.novage.p2pml.parser.hlsPlaylistParser

class Reader(private val text: String) {
    private var position = 0

    fun read(): Int {
        return if (position < text.length) {
            text[position++].code
        } else {
            -1
        }
    }

    fun readLine(): String? {
        if (position >= text.length) return null

        val start = position
        while (position < text.length) {
            val c = text[position]

            if (c == '\n' || c == '\r') break
            position++
        }

        val line = text.substring(start, position)
        if (position < text.length) {
            val c = text[position]

            position++
            if (c == '\r' && position < text.length && text[position] == '\n') {
                position++
            }
        }

        return line
    }
}
