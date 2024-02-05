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
import com.ashampoo.kim.format.png.PngChunkType
import com.ashampoo.kim.format.png.PngConstants
import com.ashampoo.kim.format.png.PngImageParser
import com.ashampoo.kim.format.png.chunk.PngTextChunk
import com.ashampoo.kim.format.tiff.constant.TiffTag
import com.ashampoo.kim.input.ByteArrayByteReader
import com.ashampoo.kim.model.ImageFormat
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
import org.w3c.dom.get
import org.w3c.dom.url.URL
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

private val thumbnailBox =
    document.getElementById("thumbnail-box") as HTMLDivElement

private val exifBox =
    document.getElementById("exif-box") as HTMLDivElement

private val iptcBox =
    document.getElementById("iptc-box") as HTMLDivElement

private val xmpBox =
    document.getElementById("xmp-box") as HTMLDivElement

private val textBox =
    document.getElementById("text-box") as HTMLDivElement

private val hexBox =
    document.getElementById("hex-box") as HTMLDivElement

private val thumbnailElement: HTMLImageElement =
    document.getElementById("thumbnail") as HTMLImageElement

private val exifElement =
    document.getElementById("exif") as Element

private val iptcElement =
    document.getElementById("iptc") as Element

private val xmpElement =
    document.getElementById("xmp") as Element

private val textElement =
    document.getElementById("text") as Element

private val hexElement =
    document.getElementById("hex") as Element

fun main() {
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

            setBoxVisibility(
                thumbnailBoxVisible = false,
                exifBoxVisible = true, // to show the message
                iptcBoxVisible = false,
                xmpBoxVisible = false,
                textBoxVisible = false,
                hexBoxVisible = false
            )

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

        val exifThumbnailBytes = metadata.getExifThumbnailBytes()

        updateThumbnail(exifThumbnailBytes, orientation)

        var displayTextChunk = false

        if (metadata.imageFormat == ImageFormat.PNG) {

            val textChunks = PngImageParser.readChunks(
                byteReader = ByteArrayByteReader(bytes),
                chunkTypeFilter = listOf(
                    PngChunkType.TEXT,
                    PngChunkType.ITXT,
                    PngChunkType.ZTXT
                )
            )

            /*
             * We are only interested in extra texts that are not
             * translated into EXIF, IPTC or XMP.
             */
            val unknownTextChunks = textChunks
                .filterIsInstance<PngTextChunk>()
                .filterNot {
                    it.getKeyword() == PngConstants.XMP_KEYWORD ||
                        it.getKeyword() == PngConstants.EXIF_KEYWORD ||
                        it.getKeyword() == PngConstants.IPTC_KEYWORD
                }

            if (unknownTextChunks.isNotEmpty()) {

                val sb = StringBuilder()

                for (chunk in unknownTextChunks) {

                    sb.appendLine("<h3>${chunk.getKeyword()}</h3>")
                    sb.appendLine(chunk.getText().escapeHtmlSpecialChars())
                    sb.appendLine()
                }

                val text = sb.toString()

                updateHtml(textElement, text)

                displayTextChunk = text.isNotBlank()
            }
        }

        /*
         * Set all boxes visible that have meaningful content.
         */
        setBoxVisibility(
            thumbnailBoxVisible = exifThumbnailBytes != null,
            exifBoxVisible = metadata.exif != null,
            iptcBoxVisible = metadata.iptc != null,
            xmpBoxVisible = metadata.xmp != null,
            textBoxVisible = displayTextChunk,
            hexBoxVisible = true
        )

    } catch (ex: Exception) {

        ex.printStackTrace()

        updateAll("Parsing error: ${ex.message}")
        updateThumbnail(null, TiffOrientation.STANDARD)

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

    if (imageBytes != null) {

        val blob = imageBytes.toBlob("image/jpeg")

        val url = URL.Companion.createObjectURL(blob)

        thumbnailElement.src = url

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

        thumbnailElement.style.transform = styleTransform

        /*
         * After the image was loaded, we can use the dimensions to scale
         * the image down to match the parent container's size
         */
        thumbnailElement.onload = {

            when (orientation) {
                TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_LEFT, TiffOrientation.ROTATE_RIGHT,
                TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_RIGHT, TiffOrientation.ROTATE_LEFT -> {

                    val naturalWidth = thumbnailElement.width * 1.0
                    val naturalHeight = thumbnailElement.height * 1.0

                    val scale = if (naturalWidth > naturalHeight) (naturalHeight / naturalWidth) else 1

                    thumbnailElement.style.transform += " scale($scale)"
                }

                else -> {}
            }
        }

    } else {

        thumbnailElement.src = ""
    }
}

private fun setBoxVisibility(
    thumbnailBoxVisible: Boolean,
    exifBoxVisible: Boolean,
    iptcBoxVisible: Boolean,
    xmpBoxVisible: Boolean,
    textBoxVisible: Boolean,
    hexBoxVisible: Boolean
) {

    thumbnailBox.style.display = cssDisplayValue(thumbnailBoxVisible)
    exifBox.style.display = cssDisplayValue(exifBoxVisible)
    iptcBox.style.display = cssDisplayValue(iptcBoxVisible)
    xmpBox.style.display = cssDisplayValue(xmpBoxVisible)
    textBox.style.display = cssDisplayValue(textBoxVisible)
    hexBox.style.display = cssDisplayValue(hexBoxVisible)
}

private fun cssDisplayValue(shouldDisplay: Boolean) =
    if (shouldDisplay) "block" else "none"

private fun makeAllBoxesVisible() =
    setBoxVisibility(
        thumbnailBoxVisible = true,
        exifBoxVisible = true,
        iptcBoxVisible = true,
        xmpBoxVisible = true,
        textBoxVisible = false,
        hexBoxVisible = true
    )

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
