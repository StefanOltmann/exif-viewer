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

import com.ashampoo.kim.Kim
import com.ashampoo.kim.format.tiff.constant.TiffTag
import com.ashampoo.kim.model.TiffOrientation
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.w3c.dom.DragEvent
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSpanElement
import org.w3c.dom.asList
import org.w3c.dom.get
import org.w3c.dom.url.URL
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

private var exifElement: Element? = null
private var iptcElement: Element? = null
private var xmpElement: Element? = null
private var hexElement: Element? = null

private var htmlThumbnailImageElement: HTMLImageElement? = null

fun main() {

    exifElement = document.getElementById("exif")
    iptcElement = document.getElementById("iptc")
    xmpElement = document.getElementById("xmp")
    hexElement = document.getElementById("hex")

    htmlThumbnailImageElement = document.getElementById("thumbnail") as? HTMLImageElement

    registerFileInputEvents()
}

private fun registerFileInputEvents() {

    val dropbox = document.getElementById("dropbox")
    val fileInput = document.getElementById("fileInput") as? HTMLElement

    dropbox?.addEventListener("dragover") { event ->

        event as DragEvent

        event.preventDefault()
        event.dataTransfer?.dropEffect = "copy"
        dropbox.classList.add("highlight")
    }

    dropbox?.addEventListener("dragleave") { event ->

        event as DragEvent

        event.preventDefault()
        dropbox.classList.remove("highlight")
    }

    dropbox?.addEventListener("drop") { event ->

        event as DragEvent

        event.preventDefault();
        dropbox.classList.remove("highlight");

        val items = event.dataTransfer?.items;

        if (items == null || items.length == 0)
            return@addEventListener

        handleFile(items[0]!!.getAsFile()!!)
    }

    dropbox?.addEventListener("click") { _ ->
        fileInput?.click()
    }

    fileInput?.addEventListener("change") { event ->

        val target = event.target as? HTMLInputElement ?: return@addEventListener

        val files = target.files

        if (files == null || files.length == 0)
            return@addEventListener

        handleFile(files[0]!!)
    }
}

private fun handleFile(file: File) {

    val fileReader = FileReader()

    fileReader.onload = { event ->

        val target = event.target as? FileReader

        if (target != null) {

            val arrayBuffer = target.result as? ArrayBuffer

            if (arrayBuffer != null) {

                val uInt8Bytes = Uint8Array(arrayBuffer)

                processFile(uInt8Bytes)
            }
        }
    }

    fileReader.readAsArrayBuffer(file)
}

private fun processFile(uint8Array: Uint8Array) {

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

        /*
         * The HEX view is an extra and may have its own problems.
         * Don't fail everything if generating this view fails.
         */
        try {

            updateHtml(hexElement, generateHexHtml(bytes))

            /*
             * The event listeners for the spans should be added after setting them
             * as innerHTML, as they become part of the DOM at that point.
             */
            addHoverListenersToSpans()

        } catch (ex: Exception) {

            ex.printStackTrace()

            updateHtml(hexElement, "Failed to generate HEX view: ${ex.message}")
        }

        val orientation: TiffOrientation = TiffOrientation.of(
            metadata.findShortValue(TiffTag.TIFF_TAG_ORIENTATION)?.toInt()
        ) ?: TiffOrientation.STANDARD

        updateThumbnail(metadata.getExifThumbnailBytes(), orientation)

    } catch (ex: Exception) {

        ex.printStackTrace()

        updateAll("Parsing error: ${ex.message}")
        updateThumbnail(null, TiffOrientation.STANDARD)

    } finally {

        /*
         * Make all boxes visible even if there is an error or
         * the error message would not be shown to the user.
         */
        makeAllBoxesVisible()
    }
}

/**
 * Adds mouseover listeners to the spans in the HEX view HTML.
 */
private fun addHoverListenersToSpans() {

    val spans = document.querySelectorAll(".hex-box span")

    for (i in 0..spans.length) {

        val span: HTMLSpanElement = spans[i] as? HTMLSpanElement ?: continue

        span.addEventListener("mouseover") {

            val cssClass = span.classList[0] ?: return@addEventListener

            val spansWithSameClass = document.querySelectorAll(".$cssClass")

            for (j in 0..spansWithSameClass.length) {

                val sameClassSpan = spansWithSameClass[j] as? HTMLSpanElement ?: continue

                sameClassSpan.classList.add("marked")
            }
        }

        span.addEventListener("mouseout") {

            val cssClass = span.classList[0] ?: return@addEventListener

            val spansWithSameClass = document.querySelectorAll(".$cssClass")

            for (j in 0..spansWithSameClass.length) {

                val sameClassSpan = spansWithSameClass[j] as? HTMLSpanElement ?: continue

                sameClassSpan.classList.remove("marked")
            }
        }
    }
}

private fun updateAll(html: String) {
    updateHtml(exifElement, html)
    updateHtml(iptcElement, html)
    updateHtml(xmpElement, html)
    updateHtml(hexElement, html)
}

private fun updateHtml(element: Element?, html: String) =
    element?.let { it.innerHTML = html }

private fun updateThumbnail(imageBytes: ByteArray?, orientation: TiffOrientation) {

    htmlThumbnailImageElement?.let { imageElement ->

        if (imageBytes != null) {

            val blob = imageBytes.toBlob("image/jpeg")

            val url = URL.Companion.createObjectURL(blob)

            imageElement.src = url

            /*
             * Use CSS to rotate the image to keep the original image bytes.
             * If the user saves the image to disk it should still be identical to
             * the output of "exiftool -b -ThumbnailImage test.jpg > thumb.jpg".
             */
            val styleTransform = when (orientation) {
                TiffOrientation.MIRROR_HORIZONTAL -> "scale(-1, 1)"
                TiffOrientation.UPSIDE_DOWN -> "rotate(180deg)"
                TiffOrientation.MIRROR_VERTICAL -> "scale(1, -1)"
                TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_LEFT -> "rotate(-90deg) scale(1, -1)"
                TiffOrientation.ROTATE_RIGHT -> "rotate(90deg)"
                TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_RIGHT -> "rotate(90deg) scale(1, -1)"
                TiffOrientation.ROTATE_LEFT -> "rotate(-90deg)"
                else -> ""
            }

            imageElement.style.transform = styleTransform

            /*
             * After the image was loaded, we can use the dimensions to scale
             * the image down to match the parent container's size
             */
            imageElement.onload = {

                when (orientation) {
                    TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_LEFT, TiffOrientation.ROTATE_RIGHT,
                    TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_RIGHT, TiffOrientation.ROTATE_LEFT -> {

                        val naturalWidth = imageElement.width * 1.0
                        val naturalHeight = imageElement.height * 1.0

                        val scale = if (naturalWidth > naturalHeight) (naturalHeight / naturalWidth) else 1

                        imageElement.style.transform += " scale($scale)"
                    }

                    else -> {}
                }
            }

        } else {

            imageElement.src = ""
        }
    }
}

private fun makeAllBoxesVisible() {

    val boxes = document.querySelectorAll(".box")

    for (box in boxes.asList()) {

        box as HTMLDivElement

        box.style.display = "block"
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
private fun toggleBoxContent(boxId: String) {

    val box = document.getElementById(boxId);

    if (box != null) {

        val content = box.querySelector(".box-content") as? HTMLDivElement

        if (content != null) {

            content.style.display = if (content.style.display == "none")
                "block"
            else
                "none"

            box.classList.toggle(
                token = "collapsed",
                force = content.style.display == "none"
            )
        }
    }
}
