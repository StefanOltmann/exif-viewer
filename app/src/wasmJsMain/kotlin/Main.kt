import com.ashampoo.kim.Kim
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
            updateThumbnail(null)
            return
        }

        updateHtml(exifElement, metadata.toExifHtmlString())
        updateHtml(iptcElement, metadata.toIptcHtmlString())
        updateHtml(xmpElement, metadata.toXmpHtmlString())

        updateThumbnail(metadata.getExifThumbnailBytes())

    } catch (ex: Exception) {

        ex.printStackTrace()

        updateAll("Parsing error: ${ex.message}")
        updateThumbnail(null)
    }
}

private fun updateAll(html: String) {
    updateHtml(exifElement, html)
    updateHtml(iptcElement, html)
    updateHtml(xmpElement, html)
}

private fun updateHtml(element: Element?, html: String) =
    element?.let { it.innerHTML = html }

private fun updateThumbnail(imageBytes: ByteArray?) {

    htmlThumbnailImageElement?.let { imageElement ->

        if (imageBytes != null) {

            val blob = imageBytes.toBlob("image/jpeg")

            val url = URL.Companion.createObjectURL(blob)

            imageElement.src = url

        } else {

            imageElement.src = ""
        }
    }
}