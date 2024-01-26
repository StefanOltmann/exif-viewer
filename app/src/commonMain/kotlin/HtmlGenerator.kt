/*
 * exif-viewer
 * Copyright (C) 2024 Stefan Oltmann
 * https://stefan-oltmann.de/exif-viewer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.ashampoo.kim.common.MetadataType
import com.ashampoo.kim.common.slice
import com.ashampoo.kim.common.toHex
import com.ashampoo.kim.common.toUInt8
import com.ashampoo.kim.format.ImageMetadata
import com.ashampoo.kim.format.bmff.BoxReader
import com.ashampoo.kim.format.bmff.BoxType
import com.ashampoo.kim.format.bmff.box.ExifBox
import com.ashampoo.kim.format.bmff.box.MetaBox
import com.ashampoo.kim.format.jpeg.JpegConstants
import com.ashampoo.kim.format.jpeg.JpegSegmentAnalyzer
import com.ashampoo.kim.format.png.PngChunkType
import com.ashampoo.kim.format.png.PngConstants
import com.ashampoo.kim.format.png.PngImageParser
import com.ashampoo.kim.format.tiff.TiffDirectory
import com.ashampoo.kim.format.tiff.TiffReader
import com.ashampoo.kim.format.tiff.constant.ExifTag
import com.ashampoo.kim.format.tiff.constant.TiffConstants
import com.ashampoo.kim.format.webp.WebPChunkType
import com.ashampoo.kim.format.webp.WebPConstants
import com.ashampoo.kim.format.webp.WebPImageParser
import com.ashampoo.kim.input.ByteArrayByteReader
import com.ashampoo.kim.model.ImageFormat

/* Show byte positions up to 99 MB. Hopefully that's enough. */
private const val POS_COUNTER_LENGTH = 8

private const val SPACE: String = "&nbsp;"
private const val SEPARATOR: String = "$SPACE|$SPACE"

private const val BYTES_PER_ROW: Int = 16
private const val ROW_CHAR_LENGTH: Int = BYTES_PER_ROW * 3

private const val SHOW_HTML_OFFSETS_AS_HEX: Boolean = false

private const val PNG_CRC_BYTES_LENGTH = 4

private const val THIN_HR_HTML =
    "<hr style=\"height:1px;margin:1px;padding:0;border-width:0;" +
        "color:#eeeeee;background-color:#eeeeee\">"

private const val BOLD_HR_HTML =
    "<hr style=\"height:2px;margin:1px;padding:0;border-width:0;" +
        "color:#dddddd;background-color:#dddddd\">"

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

        val mergedDirectories = if (exif!!.makerNoteDirectory != null)
            exif!!.directories + exif!!.makerNoteDirectory!!
        else
            exif!!.directories

        for (directory in mergedDirectories) {

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
                append(entry.tagInfo?.name ?: "Unknown")
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

fun generateHexHtml(bytes: ByteArray): String {

    val format = ImageFormat.detect(bytes) ?: return "Image format was not recognized."

    return when (format) {

        ImageFormat.JPEG ->
            generateHtmlFromSlices(bytes, createJpegSlices(bytes))

        ImageFormat.TIFF ->
            generateHtmlFromSlices(bytes, createTiffSlices(bytes, exifBytes = false))

        ImageFormat.PNG ->
            generateHtmlFromSlices(bytes, createPngSlices(bytes))

        ImageFormat.WEBP ->
            generateHtmlFromSlices(bytes, createWebPSlices(bytes))

        ImageFormat.HEIC, ImageFormat.AVIF, ImageFormat.JXL ->
            generateHtmlFromSlices(bytes, createBaseMediaFileFormatSlices(bytes))

        else -> "HEX view for $format is not (yet) supported."
    }
}

private fun createJpegSlices(bytes: ByteArray): List<LabeledSlice> {

    val segmentInfos = JpegSegmentAnalyzer.findSegmentInfos(ByteArrayByteReader(bytes))

    val slices = mutableListOf<LabeledSlice>()

    for (segmentInfo in segmentInfos) {

        val startPosition = segmentInfo.offset
        val endPosition = startPosition + segmentInfo.length

        /*
         * The EXIF segment is an APP1 segment that starts with the EXIF identifier code.
         */
        val isExifSegment = segmentInfo.marker == JpegConstants.JPEG_APP1_MARKER &&
            JpegConstants.EXIF_IDENTIFIER_CODE.contentEquals(
                bytes.slice(
                    startIndex = startPosition + 4,
                    count = JpegConstants.EXIF_IDENTIFIER_CODE.size
                )
            )

        if (isExifSegment) {

            val exifBytes = bytes.slice(
                startIndex = startPosition + 4 + JpegConstants.EXIF_IDENTIFIER_CODE.size,
                count = segmentInfo.length - 4 - JpegConstants.EXIF_IDENTIFIER_CODE.size
            )

            /* APP1 Header */
            slices.add(
                LabeledSlice(
                    range = startPosition until startPosition + 4,
                    label = JpegConstants.markerDescription(segmentInfo.marker) + SPACE +
                        "[${segmentInfo.length}" + SPACE + "bytes]",
                    emphasisOnFirstBytes = 2
                )
            )

            val exifHeaderStartPos = startPosition + 4
            val exifHeaderEndPos = exifHeaderStartPos + JpegConstants.EXIF_IDENTIFIER_CODE.size

            /* EXIF Identifier */
            slices.add(
                LabeledSlice(
                    range = exifHeaderStartPos until exifHeaderEndPos,
                    label = "EXIF" + SPACE + "Identifier",
                    separatorLineType = SeparatorLineType.THIN
                )
            )

            slices.addAll(
                createTiffSlices(
                    bytes = exifBytes,
                    startPosition = exifHeaderEndPos,
                    endPosition = endPosition,
                    exifBytes = true
                )
            )

        } else {

            slices.add(
                LabeledSlice(
                    range = startPosition until endPosition,
                    label = JpegConstants.markerDescription(segmentInfo.marker).escapeSpaces()
                        + SPACE + "[${segmentInfo.length}" + SPACE + "bytes]",
                    emphasisOnFirstBytes = 2,
                    separatorLineType = if (segmentInfo.marker == JpegConstants.SOI_MARKER)
                        SeparatorLineType.NONE
                    else
                        SeparatorLineType.BOLD,
                    /* Skip everything that is too long. */
                    snipAfterLineCount = when (segmentInfo.marker) {
                        /* Try to show much of a comment. */
                        JpegConstants.COM_MARKER_1, JpegConstants.COM_MARKER_2 -> 10
                        /* Display more of IPTC if it's not too long. */
                        JpegConstants.JPEG_APP13_MARKER -> 10 // 12 lines in total
                        /* Show the beginning of XMP */
                        JpegConstants.JPEG_APP1_MARKER -> 6 // 8 lines in total
                        /* Shorten everything else (like SOS) */
                        else -> 1
                    }
                )
            )
        }
    }

    /* For safety sort in offset order. */
    slices.sortBy { it.range.first }

    return slices
}

private fun createPngSlices(bytes: ByteArray): List<LabeledSlice> {

    val chunks = PngImageParser.readChunks(
        byteReader = ByteArrayByteReader(bytes),
        chunkTypeFilter = null
    )

    val slices = mutableListOf<LabeledSlice>()

    slices.add(
        LabeledSlice(
            range = 0 until PngConstants.PNG_SIGNATURE.size,
            label = "PNG${SPACE}signature",
            separatorLineType = SeparatorLineType.NONE
        )
    )

    var startPosition = PngConstants.PNG_SIGNATURE.size

    for (chunk in chunks) {

        slices.add(
            LabeledSlice(
                range = startPosition until startPosition + 8,
                label = chunk.type.name + SPACE + "chunk" + SPACE + "marker",
                separatorLineType = SeparatorLineType.BOLD
            )
        )

        val dataOffset = startPosition + 8

        val crcOffset = dataOffset + chunk.bytes.size

        if (chunk.type == PngChunkType.EXIF) {

            slices.addAll(
                createTiffSlices(
                    bytes = chunk.bytes,
                    startPosition = dataOffset,
                    endPosition = crcOffset,
                    exifBytes = true
                )
            )

        } else if (chunk.bytes.isNotEmpty()) {

            slices.add(
                LabeledSlice(
                    range = dataOffset until crcOffset,
                    label = chunk.type.name + SPACE + "data" +
                        SPACE + "[${chunk.bytes.size}" + SPACE + "bytes]",
                    /* Basically skip IDAT, but show more of other types. */
                    snipAfterLineCount = if (chunk.type == PngChunkType.IDAT) 1 else 5,
                    separatorLineType = SeparatorLineType.NONE
                )
            )
        }

        slices.add(
            LabeledSlice(
                range = crcOffset until crcOffset + PNG_CRC_BYTES_LENGTH,
                label = chunk.type.name + SPACE + "CRC",
                separatorLineType = SeparatorLineType.NONE
            )
        )

        startPosition = crcOffset + PNG_CRC_BYTES_LENGTH
    }

    /* For safety sort in offset order. */
    slices.sortBy { it.range.first }

    return slices
}

private fun createWebPSlices(bytes: ByteArray): List<LabeledSlice> {

    val chunks = WebPImageParser.readChunks(
        byteReader = ByteArrayByteReader(bytes)
    )

    val slices = mutableListOf<LabeledSlice>()

    slices.add(
        LabeledSlice(
            range = 0 until WebPConstants.RIFF_SIGNATURE.size,
            label = "RIFF${SPACE}signature",
            separatorLineType = SeparatorLineType.NONE
        )
    )

    slices.add(
        LabeledSlice(
            range = WebPConstants.RIFF_SIGNATURE.size until 8,
            label = "length",
            separatorLineType = SeparatorLineType.THIN
        )
    )

    slices.add(
        LabeledSlice(
            range = 8 until 12,
            label = "WEBP${SPACE}signature",
            separatorLineType = SeparatorLineType.THIN
        )
    )

    var startPosition = WebPConstants.RIFF_SIGNATURE.size +
        WebPConstants.CHUNK_SIZE_LENGTH +
        WebPConstants.WEBP_SIGNATURE.size

    for (chunk in chunks) {

        slices.add(
            LabeledSlice(
                range = startPosition until startPosition + 8,
                label = chunk.type.name + SPACE + "chunk" + SPACE + "marker",
                emphasisOnFirstBytes = 4,
                separatorLineType = SeparatorLineType.BOLD
            )
        )

        val dataOffset = startPosition + WebPConstants.TPYE_LENGTH + WebPConstants.CHUNK_SIZE_LENGTH

        /*
         * WebP chunk lengths must be an even number
         */
        val paddingByteCount = (if (chunk.bytes.size % 2 == 0) 0 else 1)

        val endPosition = dataOffset + chunk.bytes.size + paddingByteCount

        if (chunk.type == WebPChunkType.EXIF) {

            slices.addAll(
                createTiffSlices(
                    bytes = chunk.bytes,
                    startPosition = dataOffset,
                    endPosition = endPosition,
                    exifBytes = true
                )
            )

        } else if (chunk.bytes.isNotEmpty()) {

            slices.add(
                LabeledSlice(
                    range = dataOffset until endPosition,
                    label = chunk.type.name + SPACE + "data" +
                        SPACE + "[${chunk.bytes.size}" + SPACE + "bytes]",
                    snipAfterLineCount = if (chunk.type == WebPChunkType.XMP) 5 else 1,
                    separatorLineType = SeparatorLineType.NONE
                )
            )
        }

        startPosition = endPosition
    }

    /* For safety sort in offset order. */
    slices.sortBy { it.range.first }

    return slices
}

private fun createTiffSlices(
    bytes: ByteArray,
    startPosition: Int = 0,
    endPosition: Int = bytes.size,
    exifBytes: Boolean = true
): List<LabeledSlice> {

    val slices = mutableListOf<LabeledSlice>()

    val tiffContents = TiffReader.read(bytes)

    val tiffHeader = tiffContents.header

    val tiffHeaderEndPos = startPosition + TiffConstants.TIFF_HEADER_SIZE

    /* TIFF Header */
    slices.add(
        LabeledSlice(
            range = startPosition until tiffHeaderEndPos,
            label = "TIFF Header v${tiffHeader.tiffVersion}, ${tiffHeader.byteOrder.name}"
                .escapeSpaces(),
            separatorLineType = if (exifBytes) SeparatorLineType.THIN else SeparatorLineType.NONE
        )
    )

    val mergedDirectories = if (tiffContents.makerNoteDirectory != null)
        tiffContents.directories + tiffContents.makerNoteDirectory!!
    else
        tiffContents.directories

    for (directory in mergedDirectories) {

        val directoryDescription = if (directory.type == 1)
            "IFD1" // Workaround for bad name in Kim
        else
            TiffDirectory.description(directory.type)

        val directoryOffset = directory.offset + startPosition

        directory.jpegImageDataElement?.let {

            val offset = it.offset + startPosition

            slices.add(
                LabeledSlice(
                    range = offset until offset + it.length,
                    label = "[$directoryDescription thumbnail: ${it.length} bytes]".escapeSpaces(),
                    snipAfterLineCount = 1
                )
            )
        }

        slices.add(
            LabeledSlice(
                range = directoryOffset until directoryOffset + 2,
                label = ("$directoryDescription [${directory.entries.size} entries]")
                    .escapeSpaces(),
                separatorLineType = if (exifBytes) SeparatorLineType.THIN else SeparatorLineType.BOLD
            )
        )

        for (field in directory.entries) {

            val offset = field.offset + startPosition

            val adjustedValueOffset = field.valueOffset?.let {
                it + startPosition
            }

            val labelBase = "$directoryDescription-" +
                "${field.sortHint.toString().padStart(2, '0')} " +
                "${field.tagFormatted} " +
                (field.tagInfo?.name ?: "Unknown")

            val label = if (adjustedValueOffset != null)
                "$labelBase$SPACE(&rarr;$adjustedValueOffset)".escapeSpaces()
            else
                labelBase.escapeSpaces()

            /* Only highlight overflow values. */
            val highlightId = if (field.valueOffset != null)
                "$directoryDescription-${field.sortHint}"
            else
                null

            slices.add(
                LabeledSlice(
                    range = offset until offset + TiffConstants.TIFF_ENTRY_LENGTH,
                    label = label,
                    separatorLineType = SeparatorLineType.NONE,
                    highlightId = highlightId,
                    highlightLabel = true,
                    labelTooltip = field.valueDescription
                )
            )

            field.valueOffset?.let { valueOffset ->

                val skipMakerNoteValue =
                    ExifTag.EXIF_TAG_MAKER_NOTE == field.tagInfo &&
                    tiffContents.makerNoteDirectory != null

                if (skipMakerNoteValue)
                    return@let

                val adjValueOffset = valueOffset + startPosition

                slices.add(
                    LabeledSlice(
                        range = adjValueOffset until adjValueOffset + field.valueBytes.size,
                        label = "${field.tagInfo?.name ?: field.tagFormatted} value".escapeSpaces(),
                        /* Skip very long value fields like Maker Note or XMP (in TIFF) */
                        snipAfterLineCount = 8,
                        separatorLineType = SeparatorLineType.NONE,
                        highlightId = highlightId,
                        highlightLabel = false
                    )
                )
            }
        }

        val nextIfdOffset = directoryOffset + 2 +
            directory.entries.size * TiffConstants.TIFF_ENTRY_LENGTH

        slices.add(
            LabeledSlice(
                range = nextIfdOffset until nextIfdOffset + 4,
                label = "Next IFD offset".escapeSpaces(),
                separatorLineType = SeparatorLineType.NONE
            )
        )
    }

    /* Sort in offset order. */
    val sortedSubSlices = slices.sortedBy { it.range.first }

    var lastSliceEnd = tiffHeaderEndPos - 1

    /* Find gabs and add them. */
    for (subSlice in sortedSubSlices) {

        if (subSlice.range.first > lastSliceEnd + 1) {

            val byteCount = subSlice.range.first - lastSliceEnd - 1

            slices.add(
                LabeledSlice(
                    range = lastSliceEnd + 1 until subSlice.range.first,
                    label = if (byteCount == 1)
                        "[pad${SPACE}byte]"
                    else
                        "[unknown$SPACE$byteCount${SPACE}bytes]",
                    snipAfterLineCount = 3,
                    separatorLineType = SeparatorLineType.NONE
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
                label = if (trailingByteCount == 1)
                    "[pad${SPACE}byte]"
                else
                    "[unknown$SPACE$trailingByteCount${SPACE}bytes]",
                snipAfterLineCount = 2,
                separatorLineType = SeparatorLineType.NONE
            )
        )
    }

    /* Sort in offset order. */
    slices.sortBy { it.range.first }

    /* Add all to the result. */
    return slices
}

private fun createBaseMediaFileFormatSlices(bytes: ByteArray): List<LabeledSlice> {

    val boxes = BoxReader.readBoxes(
        byteReader = ByteArrayByteReader(bytes),
        stopAfterMetadataRead = false,
        offsetShift = 0
    )

    val metaBox = boxes.find { it.type == BoxType.META } as? MetaBox

    val metadataOffsets = metaBox?.findMetadataOffsets()

    val slices = mutableListOf<LabeledSlice>()

    for (box in boxes) {

        if (box == metaBox) {

            box as MetaBox

            val firstBoxOffset = box.boxes.first().offset.toInt()

            slices.add(
                LabeledSlice(
                    range = box.offset.toInt() until firstBoxOffset,
                    label = "Box" + SPACE + "meta" + SPACE + "header",
                    separatorLineType = SeparatorLineType.BOLD,
                    snipAfterLineCount = 3
                )
            )

            val lastSubBox = box.boxes.last()

            for (subBox in box.boxes) {

                val separatorLineType = if (subBox == lastSubBox)
                    SeparatorLineType.BOLD
                else
                    SeparatorLineType.THIN

                val subBoxRange =
                    subBox.offset.toInt() until subBox.offset.toInt() + subBox.length.toInt()

                slices.add(
                    LabeledSlice(
                        range = subBoxRange,
                        label = "Box" + SPACE + subBox.type + SPACE + "[" + subBox.length + SPACE + "bytes]",
                        separatorLineType = separatorLineType,
                        snipAfterLineCount = 3
                    )
                )
            }

        } else if (box.type == BoxType.MDAT && !metadataOffsets.isNullOrEmpty()) {

            slices.add(
                LabeledSlice(
                    range = box.offset.toInt() until box.offset.toInt() + 8,
                    label = "Box" + SPACE + "mdat" + SPACE + "header",
                    separatorLineType = if (box.offset > 0)
                        SeparatorLineType.BOLD
                    else
                        SeparatorLineType.NONE,
                    snipAfterLineCount = 3
                )
            )

            for (metadataOffset in metadataOffsets) {

                val metadataRange =
                    metadataOffset.offset.toInt() until metadataOffset.offset.toInt() + metadataOffset.length.toInt()

                if (metadataOffset.type == MetadataType.EXIF) {

                    /* EXIF Identifier */
                    slices.add(
                        LabeledSlice(
                            range = metadataRange.first + 4 until metadataRange.first + 10,
                            label = "EXIF" + SPACE + "Identifier",
                            separatorLineType = SeparatorLineType.THIN
                        )
                    )

                    val exifRange = metadataRange.first + 10 until metadataRange.last

                    slices.addAll(
                        createTiffSlices(
                            bytes = bytes.sliceArray(exifRange),
                            startPosition = exifRange.first,
                            endPosition = exifRange.last,
                            exifBytes = true
                        )
                    )

                } else {

                    slices.add(
                        LabeledSlice(
                            range = metadataRange,
                            label = metadataOffset.type.toString(),
                            separatorLineType = SeparatorLineType.THIN,
                            snipAfterLineCount = 3
                        )
                    )
                }
            }

        } else if (box.type == BoxType.EXIF) {

            box as ExifBox

            slices.add(
                LabeledSlice(
                    range = box.offset.toInt() until box.offset.toInt() + 8,
                    label = "Box" + SPACE + "Exif" + SPACE + "header",
                    separatorLineType = if (box.offset > 0)
                        SeparatorLineType.BOLD
                    else
                        SeparatorLineType.NONE,
                    snipAfterLineCount = 3
                )
            )

            slices.add(
                LabeledSlice(
                    range = box.offset.toInt() + 8 until box.offset.toInt() + 12,
                    label = "Box" + SPACE + "version" + SPACE + "and" + SPACE + "flags",
                    separatorLineType = SeparatorLineType.NONE,
                    snipAfterLineCount = 1
                )
            )

            slices.addAll(
                createTiffSlices(
                    bytes = box.exifBytes,
                    startPosition = box.offset.toInt() + 12,
                    endPosition = box.offset.toInt() + box.length.toInt(),
                    exifBytes = true
                )
            )

        } else {

            val boxRange = box.offset.toInt() until box.offset.toInt() + box.length.toInt()

            slices.add(
                LabeledSlice(
                    range = boxRange,
                    label = "Box" + SPACE + box.type + SPACE + "[" + box.length + SPACE + "bytes]",
                    separatorLineType = if (box.offset > 0)
                        SeparatorLineType.BOLD
                    else
                        SeparatorLineType.NONE,
                    snipAfterLineCount = 3
                )
            )
        }
    }

    /* For safety sort in offset order. */
    slices.sortBy { it.range.first }

    return slices
}

/**
 * To prevent missing parts of the document this method
 * should check that nothing is missing or add it.
 */
private fun completeSlices(
    byteCount: Int,
    slices: List<LabeledSlice>
): List<LabeledSlice> {

    val completedSlices = mutableListOf<LabeledSlice>()

    for (slice in slices) {

        if (completedSlices.isEmpty()) {
            completedSlices.add(slice)
            continue
        }

        val lastSlice = completedSlices.last()

        val needToFillGap = slice.range.first - lastSlice.range.last > 1

        if (needToFillGap) {

            completedSlices.add(
                LabeledSlice(
                    range = lastSlice.range.last + 1 until slice.range.first,
                    label = "[unknown]",
                    separatorLineType = SeparatorLineType.THIN
                )
            )
        }

        completedSlices.add(slice)
    }

    val lastSlice = completedSlices.last()

    val needToFillToEnd = byteCount - lastSlice.range.last > 1

    if (needToFillToEnd) {

        completedSlices.add(
            LabeledSlice(
                range = lastSlice.range.last + 1 until byteCount,
                label = "[unknown]",
                separatorLineType = SeparatorLineType.THIN
            )
        )
    }

    return completedSlices
}

private fun generateHtmlFromSlices(
    bytes: ByteArray,
    slices: List<LabeledSlice>
): String = buildString {

    val completedSlices = completeSlices(bytes.size, slices)

    val spanSb = StringBuilder()

    appendLine("<div class=\"hex-box\" style=\"font-family: monospace;\">")

    for (slice in completedSlices) {

        val bytesOfLine = mutableListOf<Byte>()

        var skipToPosition: Int? = null

        var firstLineOfSegment = true

        if (slice.separatorLineType == SeparatorLineType.THIN)
            appendLine(THIN_HR_HTML)

        if (slice.separatorLineType == SeparatorLineType.BOLD)
            appendLine(BOLD_HR_HTML)

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
            if (firstLineOfSegment && bytesOfLine.size <= slice.emphasisOnFirstBytes)
                append("<b>" + byte.toHex().uppercase() + "</b>$SPACE")
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

                if (slice.highlightId != null && !slice.highlightLabel)
                    append(
                        "<span class=\"${slice.highlightId}\">" +
                            decodeBytesForHexView(bytesOfLine) + "</span>"
                    )
                else
                    append(decodeBytesForHexView(bytesOfLine))

                if (remainingByteCount > 0)
                    append(SPACE.repeat(remainingByteCount))

                append(SEPARATOR)

                /* Write segment marker info on the line where it started. */
                if (firstLineOfSegment) {

                    val hasExtras = (slice.highlightId != null && slice.highlightLabel) ||
                        slice.labelTooltip != null

                    if (hasExtras) {

                        spanSb.clear()

                        spanSb.append("<span")

                        if (slice.highlightId != null && slice.highlightLabel)
                            spanSb.append(" class=\"${slice.highlightId}\"")

                        if (slice.labelTooltip != null)
                            spanSb.append(" title=\"${slice.labelTooltip}\"")

                        spanSb.append(">")
                        spanSb.append(slice.label)
                        spanSb.append("</span>")

                        append(spanSb.toString())

                    } else {

                        append(slice.label)
                    }

                    firstLineOfSegment = false
                }

                appendLine("<br>")

                bytesOfLine.clear()

                val printedBytesCount = position - slice.range.first + 1
                val maxBytesToPrint = slice.snipAfterLineCount * BYTES_PER_ROW

                /*
                 * Start of Scan contains image data and is very long. We want to skip
                 * all these data which are not useful for a metadata hex dump.
                 */
                if (printedBytesCount >= maxBytesToPrint && position != slice.range.last) {

                    /* Skip to the end of the segment in the next iteration. */
                    skipToPosition = slice.range.last - BYTES_PER_ROW + 1

                    val byteCountToSkip = skipToPosition - position - 1

                    if (byteCountToSkip > 0) {

                        append(toPaddedPos(position) + SEPARATOR)

                        append(centerMessageInLine("[ ... snip $byteCountToSkip bytes ... ]"))

                        appendLine("$SPACE|${SPACE.repeat(18)}|<br>")
                    }
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
