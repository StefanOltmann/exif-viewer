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

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

fun Uint8Array.toByteArray(): ByteArray =
    ByteArray(length) { this[it] }

fun ByteArray.toUint8Array(): Uint8Array {
    val result = Uint8Array(size)
    forEachIndexed { index, byte ->
        result[index] = byte
    }
    return result
}

fun ByteArray.toBlob(mimeType: String): Blob {

    val uint8Array: Uint8Array = toUint8Array()

    return Blob(
        jsArrayOf(uint8Array),
        BlobPropertyBag(mimeType)
    )
}

fun <T : JsAny?> jsArrayOf(vararg elements: T): JsArray<T> {

    val array = JsArray<T>()

    for (i in elements.indices)
        array[i] = elements[i]

    return array
}
