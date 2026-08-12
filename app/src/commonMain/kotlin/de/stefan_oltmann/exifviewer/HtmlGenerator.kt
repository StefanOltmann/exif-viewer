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

package de.stefan_oltmann.exifviewer

import de.stefan_oltmann.kim.common.MetadataOffset
import de.stefan_oltmann.kim.common.MetadataType
import de.stefan_oltmann.kim.common.toFourCCTypeString
import de.stefan_oltmann.kim.format.MediaMetadata
import de.stefan_oltmann.kim.format.bmff.BoxReader
import de.stefan_oltmann.kim.format.bmff.BoxType
import de.stefan_oltmann.kim.format.bmff.box.Box
import de.stefan_oltmann.kim.format.bmff.box.ItemInfoEntryBox
import de.stefan_oltmann.kim.format.bmff.box.ItemInformationBox
import de.stefan_oltmann.kim.format.bmff.box.ItemLocationBox
import de.stefan_oltmann.kim.format.bmff.box.MetaBox
import de.stefan_oltmann.kim.format.bmff.box.MetaBoxTopLevel
import de.stefan_oltmann.kim.format.gif.GifChunkType
import de.stefan_oltmann.kim.format.gif.GifImageParser
import de.stefan_oltmann.kim.format.jpeg.JpegConstants
import de.stefan_oltmann.kim.format.jpeg.JpegSegmentAnalyzer
import de.stefan_oltmann.kim.format.jpeg.iptc.IptcMetadata
import de.stefan_oltmann.kim.format.jxl.box.ExifBox
import de.stefan_oltmann.kim.format.png.PngChunkType
import de.stefan_oltmann.kim.format.png.PngConstants
import de.stefan_oltmann.kim.format.png.PngImageParser
import de.stefan_oltmann.kim.format.tiff.TiffContents
import de.stefan_oltmann.kim.format.tiff.TiffDirectory
import de.stefan_oltmann.kim.format.tiff.TiffField
import de.stefan_oltmann.kim.format.tiff.TiffReader
import de.stefan_oltmann.kim.format.tiff.constant.ExifTag
import de.stefan_oltmann.kim.format.tiff.constant.GeoTiffTag
import de.stefan_oltmann.kim.format.tiff.constant.TiffConstants
import de.stefan_oltmann.kim.format.tiff.geotiff.GeoTiffDirectory
import de.stefan_oltmann.kim.format.webp.WebPChunkType
import de.stefan_oltmann.kim.format.webp.WebPConstants
import de.stefan_oltmann.kim.format.webp.WebPImageParser
import de.stefan_oltmann.kim.input.ByteArrayByteReader
import de.stefan_oltmann.kim.model.MediaFormat

/* Show byte positions up to 99 MB. Hopefully that's enough. */
private const val POS_COUNTER_LENGTH = 8

private const val SPACE: String = "&nbsp;"
private const val SEPARATOR: String = "$SPACE|$SPACE"

private const val BYTES_PER_ROW: Int = 16
private const val CHARS_PER_BYTE: Int = 3
private const val ROW_CHAR_LENGTH: Int = BYTES_PER_ROW * CHARS_PER_BYTE

private const val SHOW_HTML_OFFSETS_AS_HEX: Boolean = false

private const val PNG_CRC_BYTES_LENGTH = 4

/* Number of spaces between the separators of the snip message line. */
private const val SNIP_LINE_SEPARATOR_SPACES = 18

private const val BYTE_MASK = 0xFF

private const val THIN_HR_HTML =
    "<hr style=\"height:1px;margin:1px;padding:0;border-width:0;" +
        "color:#eeeeee;background-color:#eeeeee\">"

private const val BOLD_HR_HTML =
    "<hr style=\"height:2px;margin:1px;padding:0;border-width:0;" +
        "color:#dddddd;background-color:#dddddd\">"

internal fun mergeMakerNote(
    directories: List<TiffDirectory>,
    makerNoteDirectory: TiffDirectory?
): List<TiffDirectory> =
    if (makerNoteDirectory != null)
        directories + makerNoteDirectory
    else
        directories

fun MediaMetadata.toExifHtmlString(): String =
    buildExifHtmlString(exif)

internal fun buildExifHtmlString(exif: TiffContents?): String =
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

        val mergedDirectories = mergeMakerNote(exif.directories, exif.makerNoteDirectory)

        for (directory in mergedDirectories) {

            val directoryDescription = TiffDirectory.description(directory.type)

            for (entry in directory.entries) {

                append("<tr>")

                append("<td>")
                append(directoryDescription)
                append("</td>")

                append("<td>")
                append(entry.tagFormatted.escapeHtmlSpecialChars())
                append("</td>")

                append("<td>")
                append((entry.tagInfo?.name ?: "Unknown").escapeHtmlSpecialChars())
                append("</td>")

                append("<td>")
                append(entry.valueDescription.escapeHtmlSpecialChars())
                append("</td>")

                append("</tr>")
            }
        }

        append("</table>")
    }

fun MediaMetadata.toIptcHtmlString(): String =
    buildIptcHtmlString(iptc)

internal fun buildIptcHtmlString(iptc: IptcMetadata?): String =
    buildString {

        if (iptc == null) {
            append("No IPTC data.")
            return@buildString
        }

        if (iptc.records.isEmpty()) {
            append("IPTC present, but has no records.")
            return@buildString
        }

        append("<table>")

        append("<tr>")
        append("<th>ID</th>")
        append("<th>Name</th>")
        append("<th>Value</th>")
        append("</tr>")

        for (record in iptc.records) {

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

fun MediaMetadata.toXmpHtmlString(): String =
    buildXmpHtmlString(xmp)

internal fun buildXmpHtmlString(xmp: String?): String =
    buildString {

        if (xmp == null) {
            append("No XMP data.")
            return@buildString
        }

        append(
            xmp.escapeHtmlSpecialChars()
                .replace("\n", "<br>")
        )
    }

/**
 * Builds the HTML of one PNG text chunk, escaping the file-derived
 * keyword and text before they enter the DOM.
 */
internal fun buildPngTextChunkHtml(keyword: String, text: String): String =
    buildString {

        append("<h3>${keyword.escapeHtmlSpecialChars()}</h3>")

        appendLine()
        append(text.escapeHtmlSpecialChars())
        appendLine()
        appendLine()
    }

fun MediaMetadata.toGeoTiffHtmlString(): String =
    buildGeoTiffHtmlString(exif?.geoTiffDirectory)

internal fun buildGeoTiffHtmlString(geoTiffDirectory: GeoTiffDirectory?): String =
    buildString {

        if (geoTiffDirectory == null) {
            append("No GeoTiff data.")
            return@buildString
        }

        append("<table>")

        append("<tr>")
        append("<th>Name</th>")
        append("<th>Value</th>")
        append("</tr>")

        append("<tr>")
        append("<td>Version</td>")
        append("<td>${geoTiffDirectory.geoTiffVersionString}")
        append("</tr>")

        append("<tr>")
        append("<td>Model type</td>")
        append("<td>${geoTiffDirectory.modelType?.displayName ?: "-/-"}")
        append("</tr>")

        append("<tr>")
        append("<td>Raster type</td>")
        append("<td>${geoTiffDirectory.rasterType?.displayName ?: "-/-"}")
        append("</tr>")

        append("<tr>")
        append("<td>Geographic type</td>")
        append("<td>${geoTiffDirectory.geographicType?.displayName ?: "-/-"}")
        append("</tr>")

        append("</table>")
    }

fun generateHexHtml(bytes: ByteArray): String {

    val format = MediaFormat.detect(bytes) ?: return "Image format was not recognized."

    return when (format) {

        MediaFormat.JPEG ->
            generateHtmlFromSlices(bytes, createJpegSlices(bytes))

        MediaFormat.TIFF,
        MediaFormat.CR2,
        MediaFormat.NEF,
        MediaFormat.ARW,
        MediaFormat.RW2,
        MediaFormat.ORF ->
            generateHtmlFromSlices(bytes, createTiffSlices(bytes, exifBytes = false))

        MediaFormat.PNG ->
            generateHtmlFromSlices(bytes, createPngSlices(bytes))

        MediaFormat.WEBP ->
            generateHtmlFromSlices(bytes, createWebPSlices(bytes))

        MediaFormat.HEIC,
        MediaFormat.AVIF,
        MediaFormat.JXL ->
            generateHtmlFromSlices(bytes, createBaseMediaFileFormatSlices(bytes))

        MediaFormat.GIF ->
            generateHtmlFromSlices(bytes, createGifSlices(bytes))

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
                    label = JpegConstants.markerDescription(segmentInfo.marker).escapeHtmlSpecialChars()
                        + SPACE + "[${segmentInfo.length}" + SPACE + "bytes]",
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
                        JpegConstants.COM_MARKER_1 -> 10
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

internal fun createTiffSlices(
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

    val mergedDirectories = mergeMakerNote(tiffContents.directories, tiffContents.makerNoteDirectory)

    for (directory in mergedDirectories)
        slices.addAll(createTiffDirectorySlices(directory, startPosition, exifBytes, tiffContents))

    /* Find gaps and add them. */
    slices.addAll(createTiffGapSlices(slices, tiffHeaderEndPos, endPosition))

    /* Sort in offset order. */
    slices.sortBy { it.range.first }

    return slices
}

private fun createTiffDirectorySlices(
    directory: TiffDirectory,
    startPosition: Int,
    exifBytes: Boolean,
    tiffContents: TiffContents
): List<LabeledSlice> {

    val slices = mutableListOf<LabeledSlice>()

    val directoryDescription = if (directory.type == 1)
        "IFD1" // Workaround for bad name in Kim
    else
        TiffDirectory.description(directory.type)

    val directoryOffset = directory.offset + startPosition

    directory.getJpegImageDataElement()?.let {

        val offset = it.offset + startPosition

        slices.add(
            LabeledSlice(
                range = offset until offset + it.length,
                label = "[$directoryDescription thumbnail: ${it.length} bytes]".escapeSpaces(),
                snipAfterLineCount = 1
            )
        )
    }

    directory.getStripImageDataElements()?.let {

        for (element in it) {

            val offset = element.offset + startPosition

            slices.add(
                LabeledSlice(
                    range = offset until offset + element.length,
                    label = "[$directoryDescription strip bytes: ${element.length} bytes]".escapeSpaces(),
                    snipAfterLineCount = 1
                )
            )
        }
    }

    slices.add(
        LabeledSlice(
            range = directoryOffset until directoryOffset + 2,
            label = ("$directoryDescription [${directory.entries.size} entries]")
                .escapeSpaces(),
            separatorLineType = if (exifBytes) SeparatorLineType.THIN else SeparatorLineType.BOLD
        )
    )

    for (field in directory.entries)
        slices.addAll(createTiffFieldSlices(field, directoryDescription, startPosition, tiffContents))

    val nextIfdOffset = directoryOffset + 2 +
        directory.entries.size * TiffConstants.TIFF_ENTRY_LENGTH

    slices.add(
        LabeledSlice(
            range = nextIfdOffset until nextIfdOffset + 4,
            label = "Next IFD offset".escapeSpaces(),
            separatorLineType = SeparatorLineType.NONE
        )
    )

    return slices
}

private fun createTiffFieldSlices(
    field: TiffField,
    directoryDescription: String,
    startPosition: Int,
    tiffContents: TiffContents
): List<LabeledSlice> {

    val slices = mutableListOf<LabeledSlice>()

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

        val isGeoTiffDirectory =
            GeoTiffTag.EXIF_TAG_GEO_KEY_DIRECTORY_TAG == field.tagInfo

        val adjValueOffset = valueOffset + startPosition

        if (isGeoTiffDirectory) {

            slices.add(
                LabeledSlice(
                    range = adjValueOffset until adjValueOffset + 2,
                    label = "GeoTiff" + SPACE + "KeyDirectoryVersion",
                    separatorLineType = SeparatorLineType.NONE,
                    highlightId = highlightId
                )
            )

            slices.add(
                LabeledSlice(
                    range = adjValueOffset + 2 until adjValueOffset + 4,
                    label = "GeoTiff" + SPACE + "KeyRevision",
                    separatorLineType = SeparatorLineType.NONE,
                    highlightId = highlightId
                )
            )

            slices.add(
                LabeledSlice(
                    range = adjValueOffset + 4 until adjValueOffset + 6,
                    label = "GeoTiff" + SPACE + "MinorRevision",
                    separatorLineType = SeparatorLineType.NONE,
                    highlightId = highlightId
                )
            )

            slices.add(
                LabeledSlice(
                    range = adjValueOffset + 6 until adjValueOffset + 8,
                    label = "GeoTiff" + SPACE + "NumberOfKeys",
                    separatorLineType = SeparatorLineType.NONE,
                    highlightId = highlightId
                )
            )

            // TODO Explain the values
            slices.add(
                LabeledSlice(
                    range = adjValueOffset + 8 until adjValueOffset + field.valueBytes.size,
                    label = "GeoTiff" + SPACE + "values",
                    separatorLineType = SeparatorLineType.NONE,
                    highlightId = highlightId
                )
            )

        } else {

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

    return slices
}

private fun createTiffGapSlices(
    slices: List<LabeledSlice>,
    startPosition: Int,
    endPosition: Int
): List<LabeledSlice> {

    val gapSlices = mutableListOf<LabeledSlice>()

    var lastSliceEnd = startPosition - 1

    /* Find gaps and add them. */
    for (subSlice in slices.sortedBy { it.range.first }) {

        if (subSlice.range.first > lastSliceEnd + 1) {

            val byteCount = subSlice.range.first - lastSliceEnd - 1

            gapSlices.add(
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

    val trailingByteCount = endPosition - lastSliceEnd - 1

    /* Add the final gap. */
    if (trailingByteCount > 0) {

        gapSlices.add(
            LabeledSlice(
                range = lastSliceEnd + 1 until endPosition,
                label = if (trailingByteCount == 1)
                    "[pad${SPACE}byte]"
                else
                    "[unknown$SPACE$trailingByteCount${SPACE}bytes]",
                snipAfterLineCount = 2,
                separatorLineType = SeparatorLineType.NONE
            )
        )
    }

    return gapSlices
}

private fun createBaseMediaFileFormatSlices(bytes: ByteArray): List<LabeledSlice> {

    val boxes = BoxReader.readBoxes(
        byteReader = ByteArrayByteReader(bytes),
        stopAfterMetadataRead = false,
        offsetShift = 0
    )

    val metaBox = boxes.find { it.type == BoxType.META } as? MetaBoxTopLevel

    val metadataOffsets = metaBox?.findMetadataOffsets().orEmpty()

    val slices = mutableListOf<LabeledSlice>()

    for (box in boxes) {

        when {

            box is MetaBox ->
                slices.addAll(createMetaBoxSlices(box))

            box.type == BoxType.MDAT && metadataOffsets.isNotEmpty() ->
                slices.addAll(createMdatSlices(box, metadataOffsets, bytes))

            box is ExifBox ->
                slices.addAll(createExifBoxSlices(box))

            else ->
                slices.add(createGenericBoxSlice(box))
        }
    }

    /* For safety sort in offset order. */
    slices.sortBy { it.range.first }

    return slices
}

internal fun createMetaBoxSlices(metaBox: MetaBox): List<LabeledSlice> {

    val slices = mutableListOf<LabeledSlice>()

    val firstBoxOffset = metaBox.boxes.first().offset.toInt()

    slices.add(
        LabeledSlice(
            range = metaBox.offset.toInt() until firstBoxOffset,
            label = "Box" + SPACE + "meta" + SPACE + "header",
            separatorLineType = SeparatorLineType.BOLD,
            snipAfterLineCount = 3
        )
    )

    val lastSubBox = metaBox.boxes.last()

    for (subBox in metaBox.boxes) {

        when {

            subBox is ItemLocationBox ->
                slices.addAll(createItemLocationBoxSlices(subBox, metaBox))

            subBox is ItemInformationBox ->
                slices.addAll(createItemInformationBoxSlices(subBox))

            else -> {

                val separatorLineType = if (subBox == lastSubBox)
                    SeparatorLineType.BOLD
                else
                    SeparatorLineType.THIN

                val subBoxRange =
                    subBox.offset.toInt() until subBox.offset.toInt() + subBox.actualLength.toInt()

                slices.add(
                    LabeledSlice(
                        range = subBoxRange,
                        label = "Box" + SPACE + subBox.type + SPACE + "[" + subBox.actualLength + SPACE + "bytes]",
                        separatorLineType = separatorLineType,
                        snipAfterLineCount = 3
                    )
                )
            }
        }
    }

    return slices
}

internal fun createItemLocationBoxSlices(
    ilocBox: ItemLocationBox,
    metaBox: MetaBox
): List<LabeledSlice> {

    val slices = mutableListOf<LabeledSlice>()

    slices.add(
        LabeledSlice(
            range = ilocBox.offset.toInt() until ilocBox.offset.toInt() + 8,
            label = "Box" + SPACE + "iloc" + SPACE + "header",
            separatorLineType = SeparatorLineType.THIN,
            snipAfterLineCount = 3
        )
    )

    slices.add(
        LabeledSlice(
            range = ilocBox.offset.toInt() + 8 until ilocBox.offset.toInt() + 9,
            label = "Box" + SPACE + "version" + SPACE + "=" + SPACE + ilocBox.version,
            separatorLineType = SeparatorLineType.NONE,
            snipAfterLineCount = 1
        )
    )

    slices.add(
        LabeledSlice(
            range = ilocBox.offset.toInt() + 9 until ilocBox.offset.toInt() + 12,
            label = "Box" + SPACE + "flags",
            separatorLineType = SeparatorLineType.NONE,
            snipAfterLineCount = 1
        )
    )

    slices.add(
        LabeledSlice(
            range = ilocBox.offset.toInt() + 12 until ilocBox.offset.toInt() + 13,
            label = (
                "Offset size = ${ilocBox.offsetSize}, " +
                    "length size = ${ilocBox.lengthSize}"
                ).escapeHtmlSpecialChars(),
            separatorLineType = SeparatorLineType.NONE,
            snipAfterLineCount = 1
        )
    )

    slices.add(
        LabeledSlice(
            range = ilocBox.offset.toInt() + 13 until ilocBox.offset.toInt() + 14,
            label = (
                "Base offset size = ${ilocBox.baseOffsetSize}, " +
                    "index size = ${ilocBox.indexSize}"
                ).escapeHtmlSpecialChars(),
            separatorLineType = SeparatorLineType.NONE,
            snipAfterLineCount = 1
        )
    )

    val dataStartOffset: Int = if (ilocBox.version < 2) {

        slices.add(
            LabeledSlice(
                range = ilocBox.offset.toInt() + 14 until ilocBox.offset.toInt() + 16,
                label = "Item count = ${ilocBox.itemCount}".escapeHtmlSpecialChars(),
                separatorLineType = SeparatorLineType.NONE,
                snipAfterLineCount = 1
            )
        )

        ilocBox.offset.toInt() + 16

    } else {

        slices.add(
            LabeledSlice(
                range = ilocBox.offset.toInt() + 14 until ilocBox.offset.toInt() + 18,
                label = "Item count = ${ilocBox.itemCount}".escapeHtmlSpecialChars(),
                separatorLineType = SeparatorLineType.NONE,
                snipAfterLineCount = 1
            )
        )

        ilocBox.offset.toInt() + 18
    }

    // TODO Decode the rest of the box

    slices.add(
        LabeledSlice(
            range = dataStartOffset until ilocBox.offset.toInt() + metaBox.actualLength.toInt(),
            label = "data",
            separatorLineType = SeparatorLineType.NONE,
            snipAfterLineCount = 3
        )
    )

    return slices
}

internal fun createItemInformationBoxSlices(iinfBox: ItemInformationBox): List<LabeledSlice> {

    val slices = mutableListOf<LabeledSlice>()

    slices.add(
        LabeledSlice(
            range = iinfBox.offset.toInt() until iinfBox.offset.toInt() + 8,
            label = "Box" + SPACE + "iinf" + SPACE + "header",
            separatorLineType = SeparatorLineType.THIN,
            snipAfterLineCount = 3
        )
    )

    slices.add(
        LabeledSlice(
            range = iinfBox.offset.toInt() + 8 until iinfBox.offset.toInt() + 9,
            label = "Box" + SPACE + "version" + SPACE + "=" + SPACE + iinfBox.version,
            separatorLineType = SeparatorLineType.NONE,
            snipAfterLineCount = 1
        )
    )

    slices.add(
        LabeledSlice(
            range = iinfBox.offset.toInt() + 9 until iinfBox.offset.toInt() + 12,
            label = "Box" + SPACE + "flags",
            separatorLineType = SeparatorLineType.NONE,
            snipAfterLineCount = 1
        )
    )

    if (iinfBox.version == 0) {

        slices.add(
            LabeledSlice(
                range = iinfBox.offset.toInt() + 12 until iinfBox.offset.toInt() + 14,
                label = "Entry count = ${iinfBox.entryCount}".escapeHtmlSpecialChars(),
                separatorLineType = SeparatorLineType.NONE,
                snipAfterLineCount = 1
            )
        )

    } else {

        slices.add(
            LabeledSlice(
                range = iinfBox.offset.toInt() + 12 until iinfBox.offset.toInt() + 16,
                label = "Entry count = ${iinfBox.entryCount}".escapeHtmlSpecialChars(),
                separatorLineType = SeparatorLineType.NONE,
                snipAfterLineCount = 1
            )
        )
    }

    for (infeBox in iinfBox.boxes) {

        infeBox as ItemInfoEntryBox

        // FIXME Offset bug in Kim v0.14?
        val infeBoxOffset = infeBox.offset.toInt() + 2

        val subBoxRange =
            infeBoxOffset until infeBoxOffset + infeBox.actualLength.toInt()

        val itemTypeFourCC = infeBox.itemType.toFourCCTypeString()

        val itemLabel =
            "Item #${infeBox.itemId} @ ${infeBox.itemProtectionIndex} = $itemTypeFourCC"
        val label = itemLabel.escapeHtmlSpecialChars()

        slices.add(
            LabeledSlice(
                range = subBoxRange,
                label = label,
                separatorLineType = SeparatorLineType.NONE,
                snipAfterLineCount = 3
            )
        )
    }

    return slices
}

internal fun createMdatSlices(
    mdatBox: Box,
    metadataOffsets: List<MetadataOffset>,
    bytes: ByteArray
): List<LabeledSlice> {

    val slices = mutableListOf<LabeledSlice>()

    slices.add(
        LabeledSlice(
            range = mdatBox.offset.toInt() until mdatBox.offset.toInt() + 8,
            label = "Box" + SPACE + "mdat" + SPACE + "header",
            separatorLineType = if (mdatBox.offset > 0)
                SeparatorLineType.BOLD
            else
                SeparatorLineType.NONE,
            snipAfterLineCount = 3
        )
    )

    for (metadataOffset in metadataOffsets) {

        val boxRange = mdatBox.offset.toInt() until mdatBox.offset.toInt() + mdatBox.actualLength

        /*
         * Files might contain multiple MDAT boxes.
         * We only want to report extents that fall into this specific range.
         */
        if (!boxRange.contains(metadataOffset.offset))
            continue

        val metadataRange =
            metadataOffset.offset.toInt() until metadataOffset.endPosition.toInt()

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

    return slices
}

internal fun createExifBoxSlices(exifBox: ExifBox): List<LabeledSlice> {

    val slices = mutableListOf<LabeledSlice>()

    slices.add(
        LabeledSlice(
            range = exifBox.offset.toInt() until exifBox.offset.toInt() + 8,
            label = "Box" + SPACE + "Exif" + SPACE + "header",
            separatorLineType = if (exifBox.offset > 0)
                SeparatorLineType.BOLD
            else
                SeparatorLineType.NONE,
            snipAfterLineCount = 3
        )
    )

    slices.add(
        LabeledSlice(
            range = exifBox.offset.toInt() + 8 until exifBox.offset.toInt() + 12,
            label = "Box" + SPACE + "version" + SPACE + "and" + SPACE + "flags",
            separatorLineType = SeparatorLineType.NONE,
            snipAfterLineCount = 1
        )
    )

    slices.addAll(
        createTiffSlices(
            bytes = exifBox.exifBytes,
            startPosition = exifBox.offset.toInt() + 12,
            endPosition = exifBox.offset.toInt() + exifBox.actualLength.toInt(),
            exifBytes = true
        )
    )

    return slices
}

private fun createGenericBoxSlice(box: Box): LabeledSlice {

    val boxRange = box.offset.toInt() until box.offset.toInt() + box.actualLength.toInt()

    return LabeledSlice(
        range = boxRange,
        label = "Box" + SPACE + box.type + SPACE + "[" + box.actualLength + SPACE + "bytes]",
        separatorLineType = if (box.offset > 0)
            SeparatorLineType.BOLD
        else
            SeparatorLineType.NONE,
        snipAfterLineCount = 3
    )
}

private fun createGifSlices(bytes: ByteArray): List<LabeledSlice> {

    val chunks = GifImageParser.readChunks(
        byteReader = ByteArrayByteReader(bytes),
        chunkTypeFilter = null
    )

    val slices = mutableListOf<LabeledSlice>()

    var startPosition = 0

    for (chunk in chunks) {

        val endPosition = startPosition + chunk.bytes.size

        when (chunk.type) {

            GifChunkType.HEADER -> {

                slices.add(
                    LabeledSlice(
                        range = 0..2,
                        label = "GIF Signature",
                        separatorLineType = SeparatorLineType.NONE
                    )
                )

                slices.add(
                    LabeledSlice(
                        range = 3..5,
                        label = "GIF Version",
                        separatorLineType = SeparatorLineType.NONE
                    )
                )
            }

            GifChunkType.LOGICAL_SCREEN_DESCRIPTOR -> {

                slices.add(
                    LabeledSlice(
                        range = startPosition..startPosition + 1,
                        label = "Canvas Width",
                        separatorLineType = SeparatorLineType.BOLD
                    )
                )

                slices.add(
                    LabeledSlice(
                        range = startPosition + 2..startPosition + 3,
                        label = "Canvas Height",
                        separatorLineType = SeparatorLineType.NONE
                    )
                )

                slices.add(
                    LabeledSlice(
                        range = startPosition + 4..startPosition + 4,
                        label = "Packed fields",
                        separatorLineType = SeparatorLineType.NONE
                    )
                )

                slices.add(
                    LabeledSlice(
                        range = startPosition + 5..startPosition + 5,
                        label = "Background Color Index",
                        separatorLineType = SeparatorLineType.NONE
                    )
                )

                slices.add(
                    LabeledSlice(
                        range = startPosition + 6..startPosition + 6,
                        label = "Pixel Aspect Ratio",
                        separatorLineType = SeparatorLineType.NONE
                    )
                )
            }

            GifChunkType.APPLICATION_EXTENSION -> {

                slices.add(
                    LabeledSlice(
                        range = startPosition..startPosition,
                        label = "Extension introducer",
                        separatorLineType = SeparatorLineType.BOLD,
                        snipAfterLineCount = 1
                    )
                )

                slices.add(
                    LabeledSlice(
                        range = startPosition + 1..startPosition + 1,
                        label = "Application extension",
                        separatorLineType = SeparatorLineType.NONE,
                        snipAfterLineCount = 1
                    )
                )

                slices.add(
                    LabeledSlice(
                        range = startPosition + 2 until endPosition,
                        label = "data",
                        separatorLineType = SeparatorLineType.THIN,
                        snipAfterLineCount = 5
                    )
                )
            }

            else -> {

                slices.add(
                    LabeledSlice(
                        range = startPosition until endPosition,
                        label = chunk.type.name,
                        separatorLineType = SeparatorLineType.BOLD,
                        snipAfterLineCount = 1
                    )
                )
            }
        }

        startPosition = endPosition
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

/**
 * Builds the HEX view HTML for the given bytes and their labeled slices.
 */
private fun generateHtmlFromSlices(
    bytes: ByteArray,
    slices: List<LabeledSlice>
): String = buildString {

    val completedSlices = completeSlices(bytes.size, slices)

    appendLine("""<div class="hex-box" style="font-family: monospace;">""")

    for (slice in completedSlices)
        appendSliceHtml(bytes, slice)

    appendLine("</div>")
}

/**
 * Appends the HEX view lines of the slice, including the separating
 * horizontal rule and the snip message line when the byte limit is reached.
 */
private fun StringBuilder.appendSliceHtml(bytes: ByteArray, slice: LabeledSlice) {

    appendSeparatorLine(slice.separatorLineType)

    var position = slice.range.first

    while (position <= slice.range.last) {

        val lineEnd = minOf(position + BYTES_PER_ROW - 1, slice.range.last)

        appendHexLine(
            bytes = bytes,
            slice = slice,
            lineStart = position,
            lineEnd = lineEnd,
            firstLineOfSegment = position == slice.range.first
        )

        position = nextPositionAfterSnip(slice, lineEnd)
    }
}

/**
 * Appends the snip message line when the printed byte limit of the slice is
 * reached and returns the position of the first byte of the next line.
 */
private fun StringBuilder.nextPositionAfterSnip(slice: LabeledSlice, lineEnd: Int): Int {

    val printedBytesCount = lineEnd - slice.range.first + 1
    val maxBytesToPrint = slice.snipAfterLineCount * BYTES_PER_ROW

    val lastLineStart = slice.range.last - BYTES_PER_ROW + 1
    val byteCountToSkip = lastLineStart - lineEnd - 1

    val snipLimitReached = printedBytesCount >= maxBytesToPrint && lineEnd != slice.range.last

    if (!snipLimitReached || byteCountToSkip <= 0)
        return lineEnd + 1

    append(toPaddedPos(lineEnd) + SEPARATOR)

    append(centerMessageInLine("[ ... snip $byteCountToSkip bytes ... ]"))

    appendLine("$SPACE|${SPACE.repeat(SNIP_LINE_SEPARATOR_SPACES)}|<br>")

    return lastLineStart
}

/**
 * Appends one HEX view line for the byte range of the slice, including the
 * decoded ASCII area and the segment label on the line where it started.
 */
private fun StringBuilder.appendHexLine(
    bytes: ByteArray,
    slice: LabeledSlice,
    lineStart: Int,
    lineEnd: Int,
    firstLineOfSegment: Boolean
) {

    append(toPaddedPos(lineStart) + SEPARATOR)

    for (position in lineStart..lineEnd) {

        val positionInLine = position - lineStart + 1

        val byte = bytes[position]

        /* Emphasis on the marker bytes. */
        if (firstLineOfSegment && positionInLine <= slice.emphasisOnFirstBytes)
            append("<b>" + byte.toHexString(HexFormat.UpperCase) + "</b>$SPACE")
        else
            append(byte.toHexString(HexFormat.UpperCase) + SPACE)

        /* Extra spacing in the middle to have two pairs of 8 bytes. */
        if (positionInLine == BYTES_PER_ROW / 2)
            append(SPACE)
    }

    val remainingByteCount = BYTES_PER_ROW - (lineEnd - lineStart + 1)

    if (remainingByteCount > 0) {

        append(SPACE.repeat(remainingByteCount * CHARS_PER_BYTE))

        if (remainingByteCount > BYTES_PER_ROW / 2)
            append(SPACE)
    }

    append("|$SPACE")

    val decodedBytes = decodeBytesForHexView(bytes.slice(lineStart..lineEnd))

    if (slice.highlightId != null && !slice.highlightLabel)
        append("<span class=\"${slice.highlightId}\">$decodedBytes</span>")
    else
        append(decodedBytes)

    if (remainingByteCount > 0)
        append(SPACE.repeat(remainingByteCount))

    append(SEPARATOR)

    if (firstLineOfSegment)
        appendSegmentLabel(slice)

    appendLine("<br>")
}

/**
 * Appends the label of the slice, wrapped in a span when the slice uses a
 * highlight class or a tooltip.
 */
private fun StringBuilder.appendSegmentLabel(slice: LabeledSlice) {

    val hasExtras = (slice.highlightId != null && slice.highlightLabel) ||
        slice.labelTooltip != null

    if (!hasExtras) {
        append(slice.label)
        return
    }

    val labelWithExtras = buildString {

        append("<span")

        if (slice.highlightId != null && slice.highlightLabel)
            append(" class=\"${slice.highlightId}\"")

        if (slice.labelTooltip != null)
            append(" title=\"${slice.labelTooltip.escapeHtmlSpecialChars()}\"")

        append(">")
        append(slice.label)
        append("</span>")
    }

    append(labelWithExtras)
}

/**
 * Appends the horizontal rule that separates the slice from the previous
 * one, if its separator line type requires one.
 */
private fun StringBuilder.appendSeparatorLine(separatorLineType: SeparatorLineType) {

    if (separatorLineType == SeparatorLineType.THIN)
        appendLine(THIN_HR_HTML)

    if (separatorLineType == SeparatorLineType.BOLD)
        appendLine(BOLD_HR_HTML)
}

private fun centerMessageInLine(message: String): String {

    val neededWhitespace = ROW_CHAR_LENGTH - message.length

    val whitespaceBefore = neededWhitespace / 2
    val whitespaceAfter = ROW_CHAR_LENGTH - message.length - whitespaceBefore

    return SPACE.repeat(whitespaceBefore) + message + SPACE.repeat(whitespaceAfter)
}

private fun toPaddedPos(pos: Int) =
    if (SHOW_HTML_OFFSETS_AS_HEX)
        pos.toHexString()
    else
        pos.toString().padStart(POS_COUNTER_LENGTH, '0')

fun String.escapeHtmlSpecialChars(): String =
    this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .escapeSpaces()

private fun String.escapeSpaces(): String =
    this.replace(" ", SPACE)

private fun Byte.toUInt8(): Int = BYTE_MASK and toInt()

fun ByteArray.slice(startIndex: Int, count: Int): ByteArray {
    val endIndex = (startIndex + count).coerceAtMost(size)
    return sliceArray(startIndex until endIndex)
}

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
