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
