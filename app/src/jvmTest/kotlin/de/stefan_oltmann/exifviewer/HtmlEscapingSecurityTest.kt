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

import de.stefan_oltmann.kim.format.tiff.TiffReader
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proves that metadata values from untrusted files are escaped before
 * they are injected into the DOM through innerHTML.
 */
class HtmlEscapingSecurityTest {

    /**
     * Verifies that the EXIF table escapes a script payload in a string
     * value.
     */
    @Test
    fun testExifTableEscapesXssValue() {

        val actualHtml = buildExifHtmlString(TiffReader.read(tiffWithXssValue()))

        assertTrue(actualHtml.contains("&lt;img&nbsp;src=x&nbsp;onerror=&quot;globalThis.__xss=1&quot;&gt;"))
        assertFalse(actualHtml.contains("<img src=x"))
    }

    /**
     * Verifies that the HEX view tooltip escapes a script payload so it
     * cannot break out of the title attribute.
     */
    @Test
    fun testHexViewEscapesXssTooltip() {

        val actualHtml = generateHexHtml(tiffWithXssValue())

        assertTrue(actualHtml.contains("title=\"&lt;img"))
        assertFalse(actualHtml.contains("title=\"<img"))
    }

    /**
     * Verifies that a PNG text chunk keyword with a script payload is
     * escaped in its heading.
     */
    @Test
    fun testPngTextChunkEscapesXssKeyword() {

        val actualHtml = buildPngTextChunkHtml(
            keyword = "<script>alert(1)</script>",
            text = "body"
        )

        assertTrue(actualHtml.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(actualHtml.contains("<script>"))
    }

    /**
     * Verifies that the HEX view escapes the RAF version bytes, which come
     * straight from an untrusted file, before they enter the DOM through
     * innerHTML.
     */
    @Test
    fun testHexViewEscapesXssRafVersion() {

        val actualHtml = generateHexHtml(rafWithXssVersion())

        assertTrue(actualHtml.contains("&lt;&quot;a&amp;"))
        assertFalse(actualHtml.contains("<\"a&"))
    }
}

/**
 * Builds a minimal little-endian TIFF whose Make value contains an XSS
 * payload.
 */
private fun tiffWithXssValue(): ByteArray {

    val value = """<img src=x onerror="globalThis.__xss=1">""".encodeToByteArray() + byteArrayOf(0)

    return byteArrayOf(
        'I'.code.toByte(), 'I'.code.toByte(),
        0x2A, 0x00,
        8, 0, 0, 0,
        1, 0,
        0x0F, 0x01,
        2, 0,
        value.size.toByte(), 0, 0, 0,
        26, 0, 0, 0,
        0, 0, 0, 0
    ) + value
}

/**
 * Builds a minimal RAF whose four version bytes contain characters that
 * are special to HTML. The directory table points at a smallest possible
 * valid embedded JPEG, so the HEX view renders the header it is built
 * from.
 */
private fun rafWithXssVersion(): ByteArray {

    val bytes = ByteArray(RAF_HEADER_LENGTH) { '0'.code.toByte() }

    "FUJIFILMCCD-RAW ".encodeToByteArray().copyInto(bytes)

    /* The version field is four bytes long. */
    "<\"a&".encodeToByteArray().copyInto(bytes, destinationOffset = 16)

    /* SOI, SOS with one component, two scan bytes, EOI. */
    val jpegBytes = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xDA.toByte(),
        0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0xFF.toByte(), 0xD9.toByte()
    )

    fun u32At(offset: Int, value: Int) {

        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    val jpegOffset = RAF_HEADER_LENGTH
    val cfaHeaderOffset = jpegOffset + jpegBytes.size

    u32At(84, jpegOffset)
    u32At(88, jpegBytes.size)
    u32At(92, cfaHeaderOffset)
    u32At(96, 1)
    u32At(100, cfaHeaderOffset + 1)
    u32At(104, 1)

    return bytes + jpegBytes + byteArrayOf(
        0, /* CFA header */
        0  /* CFA data */
    )
}

private const val RAF_HEADER_LENGTH = 108
