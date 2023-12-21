import com.ashampoo.kim.Kim
import com.ashampoo.kim.format.tiff.constants.TiffTag
import com.ashampoo.kim.model.TiffOrientation
import kotlinx.browser.document
import org.khronos.webgl.Uint8Array
import org.w3c.dom.Element
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.url.URL

private var exifElement: Element? = null
private var iptcElement: Element? = null
private var xmpElement: Element? = null

private var htmlThumbnailImageElement: HTMLImageElement? = null

fun main() {

    exifElement = document.getElementById("exif")
    iptcElement = document.getElementById("iptc")
    xmpElement = document.getElementById("xmp")

    htmlThumbnailImageElement = document.getElementById("thumbnail") as? HTMLImageElement
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun processFile(uint8Array: Uint8Array) {

    val bytes = uint8Array.toByteArray()

    try {

        val metadata = Kim.readMetadata(bytes)

        if (metadata == null) {
            updateAll("No metadata found.")
            updateThumbnail(null, TiffOrientation.STANDARD)
            return
        }

        updateHtml(exifElement, metadata.toExifHtmlString())
        updateHtml(iptcElement, metadata.toIptcHtmlString())
        updateHtml(xmpElement, metadata.toXmpHtmlString())

        val orientation: TiffOrientation = TiffOrientation.of(
            metadata.findShortValue(TiffTag.TIFF_TAG_ORIENTATION)?.toInt()
        ) ?: TiffOrientation.STANDARD

        updateThumbnail(metadata.getExifThumbnailBytes(), orientation)

    } catch (ex: Exception) {

        ex.printStackTrace()

        updateAll("Parsing error: ${ex.message}")
        updateThumbnail(null, TiffOrientation.STANDARD)
    }
}

private fun updateAll(html: String) {
    updateHtml(exifElement, html)
    updateHtml(iptcElement, html)
    updateHtml(xmpElement, html)
}

private fun updateHtml(element: Element?, html: String) =
    element?.let { it.innerHTML = html }

private fun updateThumbnail(imageBytes: ByteArray?, orientation: TiffOrientation) {

    htmlThumbnailImageElement?.let { imageElement ->

        if (imageBytes != null) {

            val blob = imageBytes.toBlob("image/jpeg")

            val url = URL.Companion.createObjectURL(blob)

            imageElement.src = url

            // FIXME If applied the transformation the image will not fit into the box.
            //   I was unable to find a solution for this so far.
//            /*
//             * Use CSS to rotate the image to keep the original image bytes.
//             * If the user saves the image to disk it should still be identical to
//             * the output of "exiftool -b -ThumbnailImage test.jpg > thumb.jpg".
//             */
//            val styleTransform = when (orientation) {
//                TiffOrientation.MIRROR_HORIZONTAL -> "scale(-1, 1)"
//                TiffOrientation.UPSIDE_DOWN -> "rotate(180deg)"
//                TiffOrientation.MIRROR_VERTICAL -> "scale(1, -1)"
//                TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_LEFT -> "rotate(-90deg) scale(1, -1)"
//                TiffOrientation.ROTATE_RIGHT -> "rotate(90deg)"
//                TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_RIGHT -> "rotate(90deg) scale(1, -1)"
//                TiffOrientation.ROTATE_LEFT -> "rotate(-90deg)"
//                else -> ""
//            }
//
//            imageElement.style.transform = styleTransform

        } else {

            imageElement.src = ""
        }
    }
}