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

/**
 * Returns the CSS `transform` value that displays a thumbnail with the
 * given EXIF orientation correctly, or an empty string for the standard
 * orientation.
 *
 * Attention: The EXIF orientation 5 means the stored rows run along the
 * visual left side and the stored columns along the visual top, which
 * requires a transpose. Orientation 7 is its mirrored counterpart and
 * requires the opposite diagonal.
 */
internal fun thumbnailCssTransform(orientation: TiffOrientation): String =
    when (orientation) {
        TiffOrientation.STANDARD -> ""
        TiffOrientation.MIRROR_HORIZONTAL -> "scale(-1, 1)"
        TiffOrientation.UPSIDE_DOWN -> "rotate(180deg)"
        TiffOrientation.MIRROR_VERTICAL -> "scale(1, -1)"
        TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_LEFT -> "rotate(90deg) scale(1, -1)"
        TiffOrientation.ROTATE_RIGHT -> "rotate(90deg)"
        TiffOrientation.MIRROR_HORIZONTAL_AND_ROTATE_RIGHT -> "rotate(-90deg) scale(1, -1)"
        TiffOrientation.ROTATE_LEFT -> "rotate(-90deg)"
    }
