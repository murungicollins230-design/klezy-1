package com.klezy.app.media

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * TorchController
 *
 * Flashlight toggle needs no runtime permission and works over the lock
 * screen — one of the genuinely few actions that does. Uses the first
 * camera with a flash unit, which is the back camera on essentially
 * every phone.
 */
object TorchController {

    private fun manager(context: Context) =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun flashCameraId(context: Context): String? =
        manager(context).cameraIdList.firstOrNull { id ->
            manager(context).getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

    private var isOn = false

    fun setTorch(context: Context, on: Boolean): Boolean {
        val id = flashCameraId(context) ?: return false
        return try {
            manager(context).setTorchMode(id, on)
            isOn = on
            true
        } catch (e: Exception) {
            false
        }
    }

    fun toggle(context: Context): Boolean = setTorch(context, !isOn)
}
