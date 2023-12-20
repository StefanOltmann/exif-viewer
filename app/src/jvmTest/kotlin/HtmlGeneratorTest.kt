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
}