import com.ashampoo.kim.common.toHex
import com.ashampoo.kim.common.toUInt8
import com.ashampoo.kim.format.ImageMetadata
import com.ashampoo.kim.format.jpeg.JpegConstants
import com.ashampoo.kim.format.jpeg.JpegSegmentAnalyzer
import com.ashampoo.kim.format.tiff.TiffDirectory
import com.ashampoo.kim.input.ByteArrayByteReader

/* Show byte positions up to 99 MB. Hopefully that's enough. */
private const val POS_COUNTER_LENGTH = 8

private const val SPACE: String = "&nbsp;"
private const val SEPARATOR: String = "$SPACE|$SPACE"

private const val BYTES_PER_ROW: Int = 16
private const val ROW_CHAR_LENGTH: Int = BYTES_PER_ROW * 3

fun ImageMetadata.toExifHtmlString(): String =
    buildString {

        if (exif == null) {
            append("No EXIF data.")
            return@buildString
        }

        append("<table>")

        append("<tr>")
        append("<th>Directory</th>")
        append("<th>Tag</th>")
        append("<th>Name</th>")
        append("<th>Value</th>")
        append("</tr>")

        for (directory in exif!!.directories) {

            val directoryDescription = TiffDirectory.description(directory.type)

            for (entry in directory.entries) {

                append("<tr>")

                append("<td>")
                append(directoryDescription)
                append("</td>")

                append("<td>")
                append(entry.tagFormatted)
                append("</td>")

                append("<td>")
                append(entry.tagInfo.name)
                append("</td>")

                append("<td>")
                append(entry.valueDescription)
                append("</td>")

                append("</tr>")
            }
        }

        append("</table>")
    }

fun ImageMetadata.toIptcHtmlString(): String =
    buildString {

        if (iptc == null) {
            append("No IPTC data.")
            return@buildString
        }

        if (iptc?.records?.isEmpty() == true) {
            append("IPTC present, but has no records.")
            return@buildString
        }

        append("<table>")

        append("<tr>")
        append("<th>ID</th>")
        append("<th>Name</th>")
        append("<th>Value</th>")
        append("</tr>")

        for (record in iptc!!.records) {

            append("<tr>")

            append("<td>")
            append(record.iptcType.type)
            append("</td>")

            append("<td>")
            append(record.iptcType.fieldName.escapeHtmlSpecialChars())
            append("</td>")

            append("<td>")
            append(record.value.escapeHtmlSpecialChars())
            append("</td>")

            append("</tr>")
        }

        append("</table>")
    }

fun ImageMetadata.toXmpHtmlString(): String =
    buildString {

        if (xmp == null) {
            append("No XMP data.")
            return@buildString
        }

        append(
            xmp.toString()
                .escapeHtmlSpecialChars()
                .replace("\n", "<br>")
        )
    }

fun ByteArray.toJpegHex(): String {

    val segmentInfos = JpegSegmentAnalyzer.findSegmentInfos(ByteArrayByteReader(this))

    return buildString {

        appendLine("<div style=\"font-family: monospace\">")

        for (segmentInfo in segmentInfos) {

            val endPosition = segmentInfo.offset + segmentInfo.length - 1

            val bytesOfLine = mutableListOf<Byte>()

            var skipToPosition: Long? = null

            var firstLineOfSegment = true

            for (position in segmentInfo.offset..endPosition) {

                if (skipToPosition != null && position < skipToPosition)
                    continue
                else
                    skipToPosition = null

                val byte = this@toJpegHex[position.toInt()]

                if (bytesOfLine.isEmpty())
                    append(toPaddedPos(position) + SEPARATOR)

                bytesOfLine.add(byte)

                /* Emphasis on the marker bytes. */
                if (firstLineOfSegment && bytesOfLine.size <= 2)
                    append("<b>" + byte.toHex().uppercase() + "</b>" + SPACE)
                else
                    append(byte.toHex().uppercase() + SPACE)

                /* Extra spacing in the middle to have two pairs of 8 bytes. */
                if (bytesOfLine.size == BYTES_PER_ROW / 2)
                    append(SPACE)

                var breakLine = bytesOfLine.size == BYTES_PER_ROW || position == endPosition

                /* Break after FF E1 marker to have EXIF or XMP header on separate line. */
                if (firstLineOfSegment &&
                    segmentInfo.marker == JpegConstants.JPEG_APP1_MARKER &&
                    bytesOfLine.size == 2
                    ) {
                    breakLine = true
                }

                if (breakLine) {

                    val remainingByteCount = BYTES_PER_ROW - bytesOfLine.size

                    if (remainingByteCount > 0) {

                        append(SPACE.repeat(remainingByteCount * 3))

                        if (remainingByteCount > 8)
                            append(SPACE)
                    }

                    append("|$SPACE")

                    append(decodeBytesForHexView(bytesOfLine))

                    if (remainingByteCount > 0)
                        append(SPACE.repeat(remainingByteCount))

                    append(SEPARATOR)

                    /* Write segment marker info on the line where it started. */
                    if (firstLineOfSegment) {

                        append(JpegConstants.markerDescription(segmentInfo.marker))
                        append(SPACE)
                        append("[${segmentInfo.length} bytes]")

                        firstLineOfSegment = false
                    }

                    appendLine("<br>")

                    bytesOfLine.clear()

                    /*
                     * Start of Scan contains image data and is very long. We want to skip
                     * all these data which are not useful for a metadata hex dump.
                     */
                    if (segmentInfo.marker == JpegConstants.SOS_MARKER && position != endPosition) {

                        /* Skip to the end of the segment in the next iteration. */
                        skipToPosition = endPosition - BYTES_PER_ROW + 1

                        val byteCountToSkip = skipToPosition - position - 1

                        append(toPaddedPos(position) + SEPARATOR)

                        append(centerMessageInLine("[ ... $byteCountToSkip bytes ... ]"))

                        append(SPACE)
                        append("|")
                        append(SPACE.repeat(16 + 2))
                        append("|")
                        append(SPACE)
                        append("Image data")

                        appendLine("<br>")
                    }
                }
            }
        }

        appendLine("</div>")
    }
}

private fun centerMessageInLine(message: String): String {

    val neededWhitespace = ROW_CHAR_LENGTH - message.length

    val whitespaceBefore = neededWhitespace / 2
    val whitespaceAfter = ROW_CHAR_LENGTH - message.length - whitespaceBefore

    return SPACE.repeat(whitespaceBefore) + message + SPACE.repeat(whitespaceAfter)
}

private fun toPaddedPos(pos: Long) =
    pos.toString().padStart(POS_COUNTER_LENGTH, '0')

fun String.escapeHtmlSpecialChars(): String =
    this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace(" ", SPACE)

@Suppress("MagicNumber")
private fun decodeBytesForHexView(bytes: List<Byte>): String =
    buildString {
        for (byte in bytes) {

            when (val intValue = byte.toUInt8()) {

                /* Use fixed space to allow multiple after another. */
                32 -> append(SPACE)

                /* Special HTML chars */
                38 -> append("&amp;")
                60 -> append("&lt;")
                62 -> append("&gt;")

                /* Range of printable chars. */
                in 32..126 -> append(intValue.toChar())

                else -> append('.')
            }
        }
    }
