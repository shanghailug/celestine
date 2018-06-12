/*
 * Copyright (C) 2018 Yu Changyuan
 *
 * This file is part of Celestine.
 *
 * Celestine is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Celestine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Celestine.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ycy.celestine

import org.opencv.core.Mat
import org.opencv.core.Rect



interface ScreenReader {
    val width: Int
    val height: Int

    val frameInterval: Long

    fun currFn(): Long

    // copy from current screen
    fun cloneScreen(): Mat

    // do diff with previous frame
    fun frameChanged(roiList: List<Rect>): Boolean

    suspend fun waitStable(n: Int, roiList: List<Rect>)
    suspend fun waitChange(n: Int, roiList: List<Rect>)

    suspend fun waitNextFrame()
}