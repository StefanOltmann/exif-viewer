import com.ashampoo.kim.common.slice
import com.ashampoo.kim.common.startsWith
import com.ashampoo.kim.common.toHex
import com.ashampoo.kim.common.toUInt8
import com.ashampoo.kim.format.ImageFormatMagicNumbers
import com.ashampoo.kim.format.ImageMetadata
import com.ashampoo.kim.format.jpeg.JpegConstants
import com.ashampoo.kim.format.jpeg.JpegSegmentAnalyzer
import com.ashampoo.kim.format.tiff.TiffDirectory
import com.ashampoo.kim.format.tiff.TiffReader
import com.ashampoo.kim.format.tiff.constants.TiffConstants
import com.ashampoo.kim.input.ByteArrayByteReader

/* Show byte positions up to 99 MB. Hopefully that's enough. */
private const val POS_COUNTER_LENGTH = 8

private const val SPACE: String = "&nbsp;"
private const val SEPARATOR: String = "$SPACE|$SPACE"

private const val BYTES_PER_ROW: Int = 16
private const val ROW_CHAR_LENGTH: Int = BYTES_PER_ROW * 3

private const val SHOW_HTML_OFFSETS_AS_HEX: Boolean = false

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

data class LabeledSlice(
    val range: IntRange,
    val label: String,
    val emphasisOnFirstBytes: Boolean,
    val skipBytes: Boolean
)

fun ByteArray.toHexHtml(): String {

    if (this.startsWith(ImageFormatMagicNumbers.jpeg))
        return generateHtmlFromSlices(this, this.toJpegSlices())

    return "At this time only supported for JPG."
}

fun ByteArray.toJpegSlices(): List<LabeledSlice> {

    val segmentInfos = JpegSegmentAnalyzer.findSegmentInfos(ByteArrayByteReader(this))

    val slices = mutableListOf<LabeledSlice>()

    for (segmentInfo in segmentInfos) {

        val startPosition = segmentInfo.offset.toInt()
        val endPosition = startPosition + segmentInfo.length

        /*
         * The EXIF segment is an APP1 segment that starts with the EXIF identifier code.
         */
        val isExifSegment = segmentInfo.marker == JpegConstants.JPEG_APP1_MARKER &&
            JpegConstants.EXIF_IDENTIFIER_CODE.contentEquals(
                this.slice(
                    startIndex = startPosition + 4,
                    count = JpegConstants.EXIF_IDENTIFIER_CODE.size
                )
            )

        if (isExifSegment) {

            val exifBytes = this.slice(
                startIndex = startPosition + 4 + JpegConstants.EXIF_IDENTIFIER_CODE.size,
                count = segmentInfo.length - 4 - JpegConstants.EXIF_IDENTIFIER_CODE.size
            )

            /* APP1 Header */
            slices.add(
                LabeledSlice(
                    range = startPosition until startPosition + 4,
                    label = JpegConstants.markerDescription(segmentInfo.marker) + SPACE +
                        "[${segmentInfo.length}" + SPACE + "bytes]",
                    emphasisOnFirstBytes = true,
                    skipBytes = false
                )
            )

            val exifHeaderStartPos = startPosition + 4
            val exifHeaderEndPos = exifHeaderStartPos + JpegConstants.EXIF_IDENTIFIER_CODE.size

            /* EXIF Identifier */
            slices.add(
                LabeledSlice(
                    range = exifHeaderStartPos until exifHeaderEndPos,
                    label = "EXIF" + SPACE + "Identifier",
                    emphasisOnFirstBytes = false,
                    skipBytes = false
                )
            )

            slices.addAll(
                exifBytes.toTiffSlices(
                    startPosition = exifHeaderEndPos,
                    endPosition = endPosition
                )
            )

        } else {

            slices.add(
                LabeledSlice(
                    range = startPosition until endPosition,
                    label = JpegConstants.markerDescription(segmentInfo.marker).escapeSpaces()
                        + SPACE + "[${segmentInfo.length}" + SPACE + "bytes]",
                    emphasisOnFirstBytes = true,
                    skipBytes = segmentInfo.marker == JpegConstants.SOS_MARKER ||
                        segmentInfo.marker == JpegConstants.JPEG_APP2_MARKER
                )
            )
        }
    }

    /* For safety sort in offset order. */
    slices.sortBy { it.range.first }

    return slices
}

private fun ByteArray.toTiffSlices(
    startPosition: Int = 0,
    endPosition: Int = this.size
): List<LabeledSlice> {

    val slices = mutableListOf<LabeledSlice>()

    val tiffContents = TiffReader.read(ByteArrayByteReader(this))

    val tiffHeader = tiffContents.header

    val tiffHeaderEndPos = startPosition + TiffConstants.TIFF_HEADER_SIZE

    /* TIFF Header */
    slices.add(
        LabeledSlice(
            range = startPosition until tiffHeaderEndPos,
            label = "TIFF Header v${tiffHeader.tiffVersion}, ${tiffHeader.byteOrder.name}".escapeSpaces(),
            emphasisOnFirstBytes = false,
            skipBytes = false
        )
    )

    for (directory in tiffContents.directories) {

        val directoryOffset = directory.offset + startPosition

        slices.add(
            LabeledSlice(
                range = directoryOffset until directoryOffset + 2,
                label = ("${TiffDirectory.description(directory.type)} " +
                    "[${directory.entries.size} entries]")
                    .escapeSpaces(),
                emphasisOnFirstBytes = false,
                skipBytes = false
            )
        )

        for (field in directory.entries) {

            val offset = field.offset + startPosition

            val adjustedValueOffset = field.valueOffset?.let {
                it + startPosition
            }

            val label = if (adjustedValueOffset != null)
                "${field.tagFormatted}$SPACE${field.tagInfo.name}$SPACE(&rarr;$adjustedValueOffset)"
            else
                "${field.tagFormatted} ${field.tagInfo.name}".escapeSpaces()

            slices.add(
                LabeledSlice(
                    range = offset until offset + TiffConstants.TIFF_ENTRY_LENGTH,
                    label = label,
                    emphasisOnFirstBytes = false,
                    skipBytes = false
                )
            )

            field.valueOffset?.let { valueOffset ->

                val adjValueOffset = valueOffset + startPosition

                slices.add(
                    LabeledSlice(
                        range = adjValueOffset until adjValueOffset + field.valueBytes.size,
                        label = "${field.tagInfo.name} value".escapeSpaces(),
                        emphasisOnFirstBytes = false,
                        skipBytes = false
                    )
                )
            }
        }

        val nextIdfOffset = directoryOffset + 2 +
            directory.entries.size * TiffConstants.TIFF_ENTRY_LENGTH

        slices.add(
            LabeledSlice(
                range = nextIdfOffset until nextIdfOffset + 4,
                label = "Next IDF offset".escapeSpaces(),
                emphasisOnFirstBytes = false,
                skipBytes = false
            )
        )
    }

    /* Sort in offset order. */
    val sortedSubSlices = slices.sortedBy { it.range.first }

    var lastSliceEnd = tiffHeaderEndPos - 1

    /* Find gabs and add them. */
    for (subSlice in sortedSubSlices) {

        if (subSlice.range.first > lastSliceEnd + 1) {

            slices.add(
                LabeledSlice(
                    range = lastSliceEnd + 1 until subSlice.range.first,
                    label = "[padding]",
                    emphasisOnFirstBytes = false,
                    skipBytes = false
                )
            )
        }

        lastSliceEnd = subSlice.range.last
    }

    val endOfLastSubSlice = slices.maxOf { it.range.last }

    val trailingByteCount = endPosition - endOfLastSubSlice - 1

    /* Add the final gap. */
    if (trailingByteCount > 0) {

        slices.add(
            LabeledSlice(
                range = endOfLastSubSlice + 1 until endPosition,
                label = "[padding]",
                emphasisOnFirstBytes = false,
                skipBytes = false
            )
        )
    }

    /* Sort in offset order. */
    slices.sortBy { it.range.first }

    /* Add all to the result. */
    return slices
}

private fun generateHtmlFromSlices(
    bytes: ByteArray,
    slices: List<LabeledSlice>
): String = buildString {

        appendLine("<div style=\"font-family: monospace; font-size: 10pt\">")

        for (slice in slices) {

            val bytesOfLine = mutableListOf<Byte>()

            var skipToPosition: Int? = null

            var firstLineOfSegment = true

            for (position in slice.range) {

                if (skipToPosition != null && position < skipToPosition)
                    continue
                else
                    skipToPosition = null

                val byte = bytes[position]

                if (bytesOfLine.isEmpty())
                    append(toPaddedPos(position) + SEPARATOR)

                bytesOfLine.add(byte)

                /* Emphasis on the marker bytes. */
                if (firstLineOfSegment && slice.emphasisOnFirstBytes && bytesOfLine.size <= 2)
                    append("<b>" + byte.toHex().uppercase() + "</b>" + SPACE)
                else
                    append(byte.toHex().uppercase() + SPACE)

                /* Extra spacing in the middle to have two pairs of 8 bytes. */
                if (bytesOfLine.size == BYTES_PER_ROW / 2)
                    append(SPACE)

                if (bytesOfLine.size == BYTES_PER_ROW || position == slice.range.last) {

                    val remainingByteCount = BYTES_PER_ROW - bytesOfLine.size

                    if (remainingByteCount > 0) {

                        append(SPACE.repeat(remainingByteCount * 3))

                        if (remainingByteCount > BYTES_PER_ROW / 2)
                            append(SPACE)
                    }

                    append("|$SPACE")

                    append(decodeBytesForHexView(bytesOfLine))

                    if (remainingByteCount > 0)
                        append(SPACE.repeat(remainingByteCount))

                    append(SEPARATOR)

                    /* Write segment marker info on the line where it started. */
                    if (firstLineOfSegment) {

                        append(slice.label)

                        firstLineOfSegment = false
                    }

                    appendLine("<br>")

                    bytesOfLine.clear()

                    /*
                     * Start of Scan contains image data and is very long. We want to skip
                     * all these data which are not useful for a metadata hex dump.
                     */
                    if (slice.skipBytes && position != slice.range.last) {

                        /* Skip to the end of the segment in the next iteration. */
                        skipToPosition = slice.range.last - BYTES_PER_ROW + 1

                        val byteCountToSkip = skipToPosition - position - 1

                        append(toPaddedPos(position) + SEPARATOR)

                        append(centerMessageInLine("[ ... $byteCountToSkip bytes ... ]"))

                        append(SPACE)
                        append("|")
                        append(SPACE.repeat(16 + 2))
                        append("|")

                        appendLine("<br>")
                    }
                }
            }
        }

        appendLine("</div>")
    }

private fun centerMessageInLine(message: String): String {

    val neededWhitespace = ROW_CHAR_LENGTH - message.length

    val whitespaceBefore = neededWhitespace / 2
    val whitespaceAfter = ROW_CHAR_LENGTH - message.length - whitespaceBefore

    return SPACE.repeat(whitespaceBefore) + message + SPACE.repeat(whitespaceAfter)
}

@OptIn(ExperimentalStdlibApi::class)
private fun toPaddedPos(pos: Int) =
    if (SHOW_HTML_OFFSETS_AS_HEX)
        pos.toHexString()
    else
        pos.toString().padStart(POS_COUNTER_LENGTH, '0')

private fun String.escapeHtmlSpecialChars(): String =
    this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .escapeSpaces()

private fun String.escapeSpaces(): String =
    this.replace(" ", SPACE)
// .replace("-", "&#8209;")

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
