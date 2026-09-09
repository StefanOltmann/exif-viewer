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

import de.stefan_oltmann.kim.model.TiffOrientation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the CSS transform of every EXIF orientation, in particular
 * that the mirrored rotations of orientations 5 and 7 use opposite
 * rotation directions.
 */
class ThumbnailCssTransformTest {

    /**
     * Verifies each orientation maps to the CSS transform that displays
     * the thumbnail correctly.
     */
    @Test
    fun testThumbnailCssTransform() {

        assertEquals("", thumbnailCssTransform(TiffOrientation.STANDARD))

        assertEquals(
            "scale(-1, 1)",
            thumbnailCssTransform(TiffOrientation.MIRROR_HORIZONTAL)
        )

        assertEquals(
            "rotate(180deg)",
            thumbnailCssTransform(TiffOrientation.UPSIDE_DOWN)
        )

        assertEquals(
            "scale(1, -1)",
            thumbnailCssTransform(TiffOrientation.MIRROR_VERTICAL)
        )

        /* Orientation 5 requires a transpose. */
        assertEquals(
            "rotate(90deg) scale(1, -1)",
            thumbnailCssTransform(TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_LEFT)
        )

        assertEquals(
            "rotate(90deg)",
            thumbnailCssTransform(TiffOrientation.ROTATE_RIGHT)
        )

        /* Orientation 7 requires the opposite diagonal of orientation 5. */
        assertEquals(
            "rotate(-90deg) scale(1, -1)",
            thumbnailCssTransform(TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_RIGHT)
        )

        assertEquals(
            "rotate(-90deg)",
            thumbnailCssTransform(TiffOrientation.ROTATE_LEFT)
        )
    }
}
