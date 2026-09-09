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

/**
 * Describes a range of bytes of the file that should be displayed
 * together in the HEX view, together with its label and appearance.
 *
 * [snipAfterLineCount] limits how many lines of the slice are printed
 * before a snip marker is shown. [Int.MAX_VALUE] never snips and is only
 * safe for slices whose size does not grow with the file size, so slices
 * that can become large must set an explicit bounded value.
 */
data class LabeledSlice(
    val range: IntRange,
    val label: String,
    val emphasisOnFirstBytes: Int = 0,
    val snipAfterLineCount: Int = Int.MAX_VALUE,
    val separatorLineType: SeparatorLineType = SeparatorLineType.BOLD,
    val highlightId: String? = null,
    val highlightLabel: Boolean = true,
    val labelTooltip: String? = null
)
