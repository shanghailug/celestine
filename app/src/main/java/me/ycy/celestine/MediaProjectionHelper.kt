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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log

class MediaProjectionHelper(activity: Activity, ireader: ImageReader) {
    val REQ_CODE = 1234

    val _mpm: MediaProjectionManager
    lateinit var _vdp: VirtualDisplay
    var _mp: MediaProjection? = null
    val _context = activity
    val _ireader = ireader

    init {
        _mpm = _context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        Log.i(Const.TAG, "width: " + Const.width(_context) +
                " height: " + Const.height(_context) +
                " dpi: " + Const.dpi(_context))
    }

    fun start() {
        val i = _mpm.createScreenCaptureIntent()
        _context.startActivityForResult(i, REQ_CODE)
    }

    fun stop() {
        _mp?.stop()
        _mp = null
    }

    private fun createVDP(mp: MediaProjection) {
        _vdp = mp.createVirtualDisplay("vdpy",
                _ireader.width, _ireader.height,
                _context.resources.displayMetrics.densityDpi,
                VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                _ireader.surface,
                null, null)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (REQ_CODE != requestCode) return

        if (resultCode != Activity.RESULT_OK) {
            Log.e(Const.TAG, "fail to open virtual display " + resultCode)
            return
        }

        if (_mp == null) {
            _mp = _mpm.getMediaProjection(resultCode, data)
            createVDP(_mp!!)
        }
    }
}