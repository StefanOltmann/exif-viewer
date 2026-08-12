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

import de.stefan_oltmann.kim.Kim
import de.stefan_oltmann.kim.common.MetadataOffset
import de.stefan_oltmann.kim.common.MetadataType
import de.stefan_oltmann.kim.format.bmff.box.ItemInformationBox
import de.stefan_oltmann.kim.format.bmff.box.ItemLocationBox
import de.stefan_oltmann.kim.format.bmff.box.MediaDataBox
import de.stefan_oltmann.kim.format.bmff.box.MetaBox
import de.stefan_oltmann.kim.format.jpeg.iptc.IptcMetadata
import de.stefan_oltmann.kim.format.jxl.box.ExifBox
import de.stefan_oltmann.kim.format.tiff.TiffReader
import de.stefan_oltmann.kim.format.tiff.geotiff.GeoTiffDirectory
import de.stefan_oltmann.kim.format.tiff.geotiff.GeoTiffGeographicType
import de.stefan_oltmann.kim.format.tiff.geotiff.GeoTiffModelType
import de.stefan_oltmann.kim.format.tiff.geotiff.GeoTiffRasterType
import kotlin.io.path.Path
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the fallback, edge, and error branches of the HTML generator that
 * the golden-file tests do not reach, such as missing metadata blocks,
 * unknown tags, and exotic file-layout variants.
 */
class HtmlGeneratorEdgeCaseTest {

    /**
     * Verifies that PDF magic bytes fall through to the unsupported-format
     * branch of [generateHexHtml].
     */
    @Test
    fun testGenerateHexHtmlPdfIsNotSupported() {

        val bytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) + ByteArray(16)

        val actualHtml = generateHexHtml(bytes)

        assertTrue(actualHtml.contains("not (yet) supported"))
    }

    /**
     * Verifies that a missing EXIF directory yields the no-data fallback.
     */
    @Test
    fun testExifHtmlWithoutExif() {

        val actualHtml = buildExifHtmlString(exif = null)

        assertTrue(actualHtml.contains("No EXIF data."))
    }

    /**
     * Verifies that an EXIF without a maker note renders its directory table.
     */
    @Test
    fun testExifHtmlWithoutMakerNote() {

        val metadata = readMetadata("photo_2.tif")

        val exif = metadata.exif

        assertNotNull(exif)
        assertNull(exif.makerNoteDirectory)

        val actualHtml = metadata.toExifHtmlString()

        assertTrue(actualHtml.contains("<table>"))
    }

    /**
     * Verifies that missing IPTC data yields the no-data fallback.
     */
    @Test
    fun testIptcHtmlWithoutIptc() {

        val actualHtml = buildIptcHtmlString(iptc = null)

        assertTrue(actualHtml.contains("No IPTC data."))
    }

    /**
     * Verifies that IPTC metadata without records yields the empty-records
     * message.
     */
    @Test
    fun testIptcHtmlWithoutRecords() {

        val actualHtml = buildIptcHtmlString(
            IptcMetadata(
                records = emptyList(),
                rawBlocks = emptyList()
            )
        )

        assertTrue(actualHtml.contains("IPTC present, but has no records."))
    }

    /**
     * Verifies that missing XMP data yields the no-data fallback.
     */
    @Test
    fun testXmpHtmlWithoutXmp() {

        val actualHtml = buildXmpHtmlString(xmp = null)

        assertTrue(actualHtml.contains("No XMP data."))
    }

    /**
     * Verifies that a missing GeoTiff directory yields the no-data fallback.
     */
    @Test
    fun testGeoTiffHtmlWithoutGeoTiff() {

        val actualHtml = buildGeoTiffHtmlString(geoTiffDirectory = null)

        assertTrue(actualHtml.contains("No GeoTiff data."))
    }

    /**
     * Verifies that [createTiffSlices] accepts its default exifBytes
     * parameter on real TIFF bytes.
     */
    @Test
    fun testTiffSlicesWithDefaultExifBytes() {

        val tiffBytes = Path("src/jvmTest/resources/photo_2.tif").readBytes()

        val slices = createTiffSlices(tiffBytes)

        assertTrue(slices.isNotEmpty())
    }

    /**
     * Verifies that the item location slicer reads the version-2 header
     * layout.
     */
    @Test
    fun testItemLocationSlicesForVersion2() {

        val metaBox = metaBoxWithHdlr()

        val itemLocationBox = ItemLocationBox(
            offset = 0,
            size = 0,
            largeSize = null,
            payload = byteArrayOf(2, 0, 0, 0, 0x44.toByte(), 0x00) +
                u32(value = 0)
        )

        val slices = createItemLocationBoxSlices(
            itemLocationBox,
            metaBox
        )

        assertTrue(slices.any { it.label.contains("Item&nbsp;count&nbsp;=&nbsp;0") })
    }

    /**
     * Verifies that the item information slicer reads the version-1 header
     * layout.
     */
    @Test
    fun testItemInformationSlicesForVersion1() {

        val itemInformationBox = ItemInformationBox(
            offset = 0,
            size = 0,
            largeSize = null,
            payload = byteArrayOf(1, 0, 0, 0) + u32(value = 0)
        )

        val slices = createItemInformationBoxSlices(itemInformationBox)

        assertTrue(slices.any { it.label.contains("Entry&nbsp;count&nbsp;=&nbsp;0") })
    }

    /**
     * Verifies that a meta box renders a trailing generic box with a bold
     * separator.
     */
    @Test
    fun testMetaSlicesWithGenericLastBox() {

        val freeBox = box(
            type = "free",
            payload = byteArrayOf(0, 0, 0, 0)
        )

        val metaBox = MetaBox(
            offset = 0,
            size = 0,
            largeSize = null,
            payload = byteArrayOf(0, 0, 0, 0) + hdlrBox() + freeBox
        )

        val slices = createMetaBoxSlices(metaBox)

        assertTrue(slices.any { it.label.contains("free") })
    }

    /**
     * Verifies that an mdat box at offset zero renders its XMP extent.
     */
    @Test
    fun testMdatSlicesWithXmpData() {

        val mdatBox = MediaDataBox(
            offset = 0,
            size = 0,
            largeSize = null,
            payload = byteArrayOf(0x4D, 0x44, 0x41, 0x54, 1, 2, 3, 4)
        )

        val metadataOffsets = listOf(
            MetadataOffset(
                type = MetadataType.XMP,
                offset = 0,
                length = 4
            )
        )

        val slices = createMdatSlices(
            mdatBox,
            metadataOffsets,
            mdatBox.payload
        )

        assertTrue(slices.any { it.label.contains("XMP") })
    }

    /**
     * Verifies that an exif box at offset zero renders its TIFF payload.
     */
    @Test
    fun testExifBoxSlicesAtOffsetZero() {

        val tiffBytes = Path("src/jvmTest/resources/photo_2.tif").readBytes()

        val exifBox = ExifBox(
            offset = 0,
            size = 0,
            largeSize = null,
            payload = byteArrayOf(0, 0, 0, 0) + tiffBytes
        )

        val slices = createExifBoxSlices(exifBox)

        assertTrue(slices.isNotEmpty())
    }

    /**
     * Verifies that unrecognized bytes yield the unknown-format message.
     */
    @Test
    fun testGenerateHexHtmlWithUnrecognizedFormat() {

        val bytes = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
        )

        val actualHtml = generateHexHtml(bytes)

        assertTrue(actualHtml.contains("not recognized"))
    }

    /**
     * Verifies that a JPEG comment segment renders with its COM label.
     */
    @Test
    fun testGenerateHexHtmlJpegWithComment() {

        val bytes = byteArrayOf(
            /* SOI */
            0xFF.toByte(), 0xD8.toByte(),
            /* COM segment with 4 comment bytes */
            0xFF.toByte(), 0xFE.toByte(), 0x00, 0x06,
            't'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
            /* SOS, image data, and EOI to form a complete file */
            0xFF.toByte(), 0xDA.toByte(), 0x01, 0x02, 0x03, 0x04,
            0xFF.toByte(), 0xD9.toByte()
        )

        val actualHtml = generateHexHtml(bytes)

        assertTrue(actualHtml.contains("COM&nbsp;(Comment)"))
    }

    /**
     * Verifies that an unknown TIFF tag renders an Unknown label and a hex
     * fallback for its value.
     */
    @Test
    fun testTiffSlicesWithUnknownTag() {

        val slices = createTiffSlices(tiffWithUnknownTag())

        assertTrue(slices.any { it.label.contains("Unknown") })
        assertTrue(slices.any { it.label.contains("0x7777") })
    }

    /**
     * Verifies that the EXIF table names an unknown tag Unknown.
     */
    @Test
    fun testExifHtmlWithUnknownTag() {

        val actualHtml = buildExifHtmlString(TiffReader.read(tiffWithUnknownTag()))

        assertTrue(actualHtml.contains("Unknown"))
    }

    /**
     * Verifies that the hex view renders an unknown TIFF tag.
     */
    @Test
    fun testGenerateHexHtmlWithUnknownTagTiff() {

        val actualHtml = generateHexHtml(tiffWithUnknownTag())

        assertTrue(actualHtml.contains("0x7777"))
    }

    /**
     * Verifies that a GeoTiff directory without model types renders the
     * placeholder.
     */
    @Test
    fun testGeoTiffHtmlWithoutDisplayModels() {

        val actualHtml = buildGeoTiffHtmlString(
            GeoTiffDirectory(
                keyDirectoryVersion = 1,
                keyRevision = 1,
                minorRevision = 1,
                modelType = null,
                rasterType = null,
                geographicType = null
            )
        )

        assertTrue(actualHtml.contains("-/-"))
    }

    /**
     * Verifies that a GeoTiff directory renders the display names of its
     * model types.
     */
    @Test
    fun testGeoTiffHtmlWithDisplayModels() {

        val actualHtml = buildGeoTiffHtmlString(
            GeoTiffDirectory(
                keyDirectoryVersion = 1,
                keyRevision = 1,
                minorRevision = 1,
                modelType = GeoTiffModelType.PROJECTED,
                rasterType = GeoTiffRasterType.PIXEL_IS_AREA,
                geographicType = GeoTiffGeographicType.GCS_ADINDAN
            )
        )

        assertTrue(actualHtml.contains("Projected"))
        assertTrue(actualHtml.contains("Pixel Is Area"))
        assertTrue(actualHtml.contains("Adindan"))
    }

    /**
     * Verifies that the GeoTiff extension renders a directory that is
     * present.
     */
    @Test
    fun testToGeoTiffHtmlStringWithGeoTiff() {

        val metadata = readMetadata("photo_8.tif")

        val exif = metadata.exif

        assertNotNull(exif)
        assertNotNull(exif.geoTiffDirectory)

        val actualHtml = metadata.toGeoTiffHtmlString()

        assertTrue(actualHtml.contains("Geographic type"))
    }

    /**
     * Verifies that the GeoTiff extension handles a missing EXIF directory.
     */
    @Test
    fun testToGeoTiffHtmlStringWithoutExif() {

        val metadata = readMetadata("photo_9.gif")

        assertNull(metadata.exif)

        val actualHtml = metadata.toGeoTiffHtmlString()

        assertTrue(actualHtml.contains("No GeoTiff data."))
    }

    /**
     * Verifies that mdat ignores metadata extents outside its byte range.
     */
    @Test
    fun testMdatSlicesIgnoreMetadataOffsetsOutsideRange() {

        val mdatBox = MediaDataBox(
            offset = 0,
            size = 0,
            largeSize = null,
            payload = byteArrayOf(0x4D, 0x44, 0x41, 0x54, 1, 2, 3, 4)
        )

        val metadataOffsets = listOf(
            MetadataOffset(
                type = MetadataType.XMP,
                offset = 100,
                length = 4
            )
        )

        val slices = createMdatSlices(
            mdatBox,
            metadataOffsets,
            mdatBox.payload
        )

        assertTrue(slices.none { it.label.contains("XMP") })
    }

    /**
     * Verifies that a base media file without a meta box renders its mdat
     * box generically.
     */
    @Test
    fun testGenerateHexHtmlWithMdatWithoutMeta() {

        val ftypBox = box(
            type = "ftyp",
            payload = "heic".encodeToByteArray() + u32(value = 0) + "mif1".encodeToByteArray()
        )

        val mdatBox = box(
            type = "mdat",
            payload = ByteArray(4)
        )

        val actualHtml = generateHexHtml(ftypBox + mdatBox)

        assertTrue(actualHtml.contains("mdat"))
    }
}

/**
 * Reads the named resource image and returns its metadata, requiring the
 * reader to accept the fixture.
 */
private fun readMetadata(fileName: String) =
    requireNotNull(Kim.readMetadata(Path("src/jvmTest/resources/$fileName").readBytes()))

/**
 * Encodes an unsigned 32-bit value as a big-endian byte array for box
 * headers.
 */
private fun u32(value: Int): ByteArray =
    byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

/**
 * Builds an ISOBMFF box with a big-endian size prefix and the given type.
 */
private fun box(type: String, payload: ByteArray): ByteArray =
    u32(8 + payload.size) + type.encodeToByteArray() + payload

/**
 * Builds a handler reference box for the picture namespace.
 */
private fun hdlrBox(): ByteArray =
    box(
        type = "hdlr",
        payload = byteArrayOf(
            0, 0, 0, 0,
            0, 0, 0, 0,
            'p'.code.toByte(), 'c'.code.toByte(), 't'.code.toByte(), 't'.code.toByte(),
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0
        )
    )

/**
 * Builds a meta box that contains a single handler reference box.
 */
private fun metaBoxWithHdlr(): MetaBox =
    MetaBox(
        offset = 0,
        size = 0,
        largeSize = null,
        payload = byteArrayOf(0, 0, 0, 0) + hdlrBox()
    )

/**
 * Builds a minimal little-endian TIFF with one unknown tag whose value does
 * not fit inline and is therefore stored at an offset.
 */
private fun tiffWithUnknownTag(): ByteArray =
    byteArrayOf(
        'I'.code.toByte(), 'I'.code.toByte(),
        0x2A, 0x00,
        8, 0, 0, 0,
        1, 0,
        0x77, 0x77,
        4, 0,
        2, 0, 0, 0,
        26, 0, 0, 0,
        0, 0, 0, 0,
        1, 0, 0, 0, 2, 0, 0, 0
    )
