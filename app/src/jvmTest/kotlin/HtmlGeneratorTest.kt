/*
 * exif-viewer
 * Copyright (C) 2023 Stefan Oltmann
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

import com.ashampoo.kim.Kim
import kotlin.io.path.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.fail

class HtmlGeneratorTest {

    @Test
    fun testToExifHtmlString() {

        val imageBytes = Path("src/jvmTest/resources/photo_1.jpg").readBytes()

        val metadata = Kim.readMetadata(imageBytes)

        assertNotNull(metadata)

        val actualHtml = metadata.toExifHtmlString()

        val expectedHtml = Path("src/jvmTest/resources/photo_1_exif.html")
            .readBytes()
            .decodeToString()

        if (expectedHtml != actualHtml) {

            Path("build/photo_1_exif.html")
                .writeText(actualHtml)

            fail("HTML photo_1_exif.html differs.")
        }
    }

    @Test
    fun testToIptcHtmlString() {

        val imageBytes = Path("src/jvmTest/resources/photo_1.jpg").readBytes()

        val metadata = Kim.readMetadata(imageBytes)

        assertNotNull(metadata)

        val actualHtml = metadata.toIptcHtmlString()

        val expectedHtml = Path("src/jvmTest/resources/photo_1_iptc.html")
            .readBytes()
            .decodeToString()

        if (expectedHtml != actualHtml) {

            Path("build/photo_1_iptc.html")
                .writeText(actualHtml)

            fail("HTML photo_1_iptc.html differs.")
        }
    }

    @Test
    fun testToXmpHtmlString() {

        val imageBytes = Path("src/jvmTest/resources/photo_1.jpg").readBytes()

        val metadata = Kim.readMetadata(imageBytes)

        assertNotNull(metadata)

        val actualHtml = metadata.toXmpHtmlString()

        val expectedHtml = Path("src/jvmTest/resources/photo_1_xmp.html")
            .readBytes()
            .decodeToString()

        if (expectedHtml != actualHtml) {

            Path("build/photo_1_xmp.html")
                .writeText(actualHtml)

            fail("HTML photo_1_xmp.html differs.")
        }
    }

    @Test
    fun testToHexHtmlJpeg() {

        val imageBytes = Path("src/jvmTest/resources/photo_1.jpg").readBytes()

        val actualHtml = imageBytes.toHexHtml()

        val expectedHtml = Path("src/jvmTest/resources/photo_1_hex.html")
            .readBytes()
            .decodeToString()

        if (expectedHtml != actualHtml) {

            Path("build/photo_1_hex.html")
                .writeText(actualHtml)

            fail("HTML photo_1_hex.html differs.")
        }
    }

    @Test
    fun testToHexHtmlJpegWithThumbnail() {

        val imageBytes = Path("src/jvmTest/resources/photo_3.jpg").readBytes()

        val actualHtml = imageBytes.toHexHtml()

        val expectedHtml = Path("src/jvmTest/resources/photo_3_hex.html")
            .readBytes()
            .decodeToString()

        if (expectedHtml != actualHtml) {

            Path("build/photo_3_hex.html")
                .writeText(actualHtml)

            fail("HTML photo_3_hex.html differs.")
        }
    }

    @Test
    fun testToHexHtmlTiff() {

        val imageBytes = Path("src/jvmTest/resources/photo_2.tif").readBytes()

        val actualHtml = imageBytes.toHexHtml()

        val expectedHtml = Path("src/jvmTest/resources/photo_2_hex.html")
            .readBytes()
            .decodeToString()

        if (expectedHtml != actualHtml) {

            Path("build/photo_2_hex.html")
                .writeText(actualHtml)

            fail("HTML photo_2_hex.html differs.")
        }
    }
}
