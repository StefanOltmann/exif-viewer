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

            for (position in segmentInfo.offset..endPosition) {

                if (skipToPosition != null && position < skipToPosition)
                    continue
                else
                    skipToPosition = null

                val byte = this@toJpegHex[position.toInt()]

                if (bytesOfLine.isEmpty())
                    append(toPaddedPos(position) + SEPARATOR)

                bytesOfLine.add(byte)

                append(byte.toHex().uppercase() + SPACE)

                /* Extra spacing */
                if (bytesOfLine.size == BYTES_PER_ROW / 2)
                    append(SPACE)

                if (bytesOfLine.size == BYTES_PER_ROW || position == endPosition) {

                    append("|$SPACE")

                    append(decodeBytesForHexView(bytesOfLine))

                    appendLine("<br>")

                    bytesOfLine.clear()

                    /*
                     * Start of Scan contains image data and is very long. We want to skip
                     * all these data which are not useful for a metadata hex dump.
                     */
                    if (segmentInfo.marker == JpegConstants.SOS_MARKER && position != endPosition) {

                        append(SPACE.repeat(POS_COUNTER_LENGTH) + SEPARATOR)

                        append("... snipped X bytes ...")

                        appendLine("<br>")

                        /* Skip all the rest. */
                        skipToPosition = endPosition - BYTES_PER_ROW + 1
                    }
                }
            }
        }

        appendLine("</div>")
    }
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
