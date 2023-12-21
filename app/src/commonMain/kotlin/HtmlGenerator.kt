import com.ashampoo.kim.common.HEX_RADIX
import com.ashampoo.kim.common.toHex
import com.ashampoo.kim.format.ImageMetadata
import com.ashampoo.kim.format.tiff.TiffDirectory

/* Show byte positions up to 99 MB. Hopefully that's enough. */
private const val POS_COUNTER_LENGTH = 8

private const val SPACE: String = "&nbsp;"
private const val SEPERATOR: String = "$SPACE|$SPACE"

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
                append(formatTag(entry.tag))
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

private fun formatTag(tag: Int): String =
    "0x" + tag.toString(HEX_RADIX).padStart(4, '0')

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

//    val imageMetadata = JpegImageParser.parseMetadata(ByteArrayByteReader(this))

    return buildString {

        appendLine("<div style=\"font-family: monospace\">")

        var position = 0

        var numbersInLine = 0

        append(toPaddedPos(position) + SEPERATOR)

        for (byte in this@toJpegHex) {

            position++

            append(byte.toHex().uppercase() + SPACE)

            numbersInLine++

            /* Extra spacing */
            if (numbersInLine == BYTES_PER_ROW / 2)
                append(SPACE)

            if (numbersInLine == BYTES_PER_ROW) {

                appendLine("<br>")
                numbersInLine = 0

                append(toPaddedPos(position) + SEPERATOR)
            }
        }

        appendLine("</div>")
    }
}

private fun toPaddedPos(pos: Int) =
    pos.toString().padStart(POS_COUNTER_LENGTH, '0')

fun String.escapeHtmlSpecialChars(): String =
    this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace(" ", SPACE)