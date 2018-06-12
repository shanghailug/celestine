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

import android.view.accessibility.AccessibilityNodeInfo
import org.opencv.core.Rect
import android.graphics.Rect as ARect


object Utils {
    fun nodeRect(n: AccessibilityNodeInfo): Rect {
        val b = ARect()
        n.getBoundsInScreen(b)

        return Rect(b.left, b.top, b.width(), b.height())
    }

}