package com.novage.p2pml.internal.parser.hlsPlaylistParser

internal class LineIterator(private val extraLines: ArrayDeque<String>, private val reader: Reader) : Iterator<String> {
    private var nextLine: String? = null

    override fun hasNext(): Boolean {
        if (nextLine != null) return true
        if (extraLines.isNotEmpty()) {
            nextLine = extraLines.removeFirst()
            return true
        }

        while (true) {
            val line = reader.readLine() ?: break
            val trimmed = line.trim()

            if (trimmed.isNotEmpty()) {
                nextLine = trimmed
                return true
            }
        }

        return false
    }

    override fun next(): String {
        if (!hasNext()) throw NoSuchElementException()

        val result = nextLine!!
        nextLine = null

        return result
    }
}
