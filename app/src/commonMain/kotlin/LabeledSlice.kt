/*
 * exif-viewer
 * Copyright (C) 2023 Stefan Oltmann
 * https://github.com/StefanOltmann/exif-viewer
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

data class LabeledSlice(
    val range: IntRange,
    val label: String,
    val emphasisOnFirstBytes: Int = 0,
    val snipAfterLineCount: Int = Int.MAX_VALUE,
    val separatorLineType: SeparatorLineType = SeparatorLineType.BOLD
)
