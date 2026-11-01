package com.nendo.argosy.libretro

object CoreSensorUnits {
    private const val STANDARD_GRAVITY = 9.80665f

    private val accelerometerScaleByCore = mapOf(
        "vbam" to STANDARD_GRAVITY
    )

    /**
     * Factor on the libretro.h accelerometer (g) for cores whose tilt math still expects m/s^2.
     * vbam: `systemUpdateMotionSensor` in src/libretro/libretro.cpp divides by 9.80665.
     */
    fun accelerometerScale(coreId: String?): Float = accelerometerScaleByCore[coreId] ?: 1f
}
