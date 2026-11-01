package com.nendo.argosy.core.input

import android.annotation.SuppressLint
import android.view.InputDevice
import java.io.BufferedReader
import java.io.InputStreamReader

enum class DetectedLayout {
    XBOX,
    NINTENDO
}

enum class DetectionSource {
    VENDOR_ID,
    DEVICE_NAME,
    SYSTEM_PROPERTY,
    BUILD_IDENTITY,
    UNKNOWN
}

data class DetectionResult(
    val layout: DetectedLayout?,
    val deviceName: String?,
    val vendorId: Int?,
    val source: DetectionSource
)

object ControllerDetector {
    private val NINTENDO_VIDS = setOf(0x057e)
    private val XBOX_VIDS = setOf(0x045e)
    private val SONY_VIDS = setOf(0x054c)

    private val NINTENDO_LAYOUT_VIDS = setOf(
        0x2dc8,
        0x20d6,
        0x0f0d
    )

    private val MULTI_MODE_NAME_PATTERNS = listOf(
        "odin",
        "ayn"
    )

    private val NINTENDO_NAME_PATTERNS = listOf(
        "nintendo",
        "switch",
        "pro controller",
        "8bitdo",
        "retroid",
        "anbernic",
        "rg351", "rg353", "rg35xx", "rg405", "rg505", "rg556", "rg arc",
        "miyoo",
        "powkiddy",
        "trimui",
        "sn30", "sf30", "zero 2", "lite 2"
    )

    private val XBOX_NAME_PATTERNS = listOf(
        "xbox",
        "x-box",
        "microsoft"
    )

    @SuppressLint("PrivateApi")
    private fun getGamepadTypeProperty(): Int? =
        getGamepadTypeViaReflection() ?: getGamepadTypeViaShell()

    private fun getGamepadTypeViaReflection(): Int? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
            (get.invoke(null, "persist.sys.gamepad.type", "0") as String).toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun getGamepadTypeViaShell(): Int? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "persist.sys.gamepad.type"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val value = reader.readLine()?.trim()?.toIntOrNull()
            reader.close()
            process.waitFor()
            value
        } catch (e: Exception) {
            android.util.Log.e("ControllerDetector", "getGamepadTypeProperty failed", e)
            null
        }
    }

    fun detectFromDevice(device: InputDevice): DetectionResult {
        val vendorId = device.vendorId
        val deviceName = device.name?.lowercase() ?: ""

        val isMultiModeDevice = MULTI_MODE_NAME_PATTERNS.any { deviceName.contains(it) }
        if (isMultiModeDevice) {
            val gamepadType = getGamepadTypeProperty()
            val layout = when (gamepadType) {
                1 -> DetectedLayout.XBOX
                else -> DetectedLayout.NINTENDO
            }
            return DetectionResult(layout, device.name, vendorId, DetectionSource.SYSTEM_PROPERTY)
        }

        when (vendorId) {
            in NINTENDO_VIDS -> return DetectionResult(
                DetectedLayout.NINTENDO, device.name, vendorId, DetectionSource.VENDOR_ID
            )
            in XBOX_VIDS, in SONY_VIDS -> return DetectionResult(
                DetectedLayout.XBOX, device.name, vendorId, DetectionSource.VENDOR_ID
            )
        }

        if (vendorId in NINTENDO_LAYOUT_VIDS) {
            return DetectionResult(
                DetectedLayout.NINTENDO, device.name, vendorId, DetectionSource.VENDOR_ID
            )
        }

        for (pattern in XBOX_NAME_PATTERNS) {
            if (deviceName.contains(pattern)) {
                return DetectionResult(
                    DetectedLayout.XBOX, device.name, vendorId, DetectionSource.DEVICE_NAME
                )
            }
        }

        for (pattern in NINTENDO_NAME_PATTERNS) {
            if (deviceName.contains(pattern)) {
                return DetectionResult(
                    DetectedLayout.NINTENDO, device.name, vendorId, DetectionSource.DEVICE_NAME
                )
            }
        }

        if (isBuiltInPad(device)) {
            detectFromBuildIdentity()?.let { layout ->
                return DetectionResult(layout, device.name, vendorId, DetectionSource.BUILD_IDENTITY)
            }
        }

        return DetectionResult(null, device.name, vendorId, DetectionSource.UNKNOWN)
    }

    /**
     * True for the handheld's own controls. Handhelds that wire their pad over an internal USB bus
     * (AYN, Retroid, AYANEO) report it external, so a handheld maker's vendor id, a pad named after
     * the device's brand, or the brand's own pad name also counts.
     */
    fun isBuiltInPad(device: InputDevice): Boolean =
        (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && !device.isExternal) ||
            isHandheldVendor(device.vendorId) ||
            padNamedForDevice(device.name, android.os.Build.MODEL, android.os.Build.MANUFACTURER)

    internal fun isHandheldVendor(vendorId: Int): Boolean = vendorId in HANDHELD_PAD_VENDORS

    private val HANDHELD_PAD_VENDORS = setOf(
        0x2020,
        0x2022,
        0x4001
    )

    internal fun padNamedForDevice(padName: String?, deviceModel: String?, manufacturer: String? = null): Boolean {
        val padBrand = padName?.trim()?.substringBefore(' ')?.lowercase() ?: return false
        val brand = deviceModel?.trim()?.substringBefore(' ')?.lowercase().orEmpty()
        if (brand.length >= MIN_BRAND_LENGTH && padBrand == brand) return true
        return padBrand == BUILT_IN_PAD_BRAND_BY_MANUFACTURER[manufacturer?.trim()?.lowercase()]
    }

    private const val MIN_BRAND_LENGTH = 3

    private val BUILT_IN_PAD_BRAND_BY_MANUFACTURER = mapOf(
        "ayn" to "odin"
    )

    /** Built-in pads on handhelds often report generic names (virtual_gamepad) and junk vendor ids; fall back to the device build. */
    private fun detectFromBuildIdentity(): DetectedLayout? {
        val identity = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".lowercase()
        if (XBOX_NAME_PATTERNS.any { identity.contains(it) }) return DetectedLayout.XBOX
        if (NINTENDO_NAME_PATTERNS.any { identity.contains(it) }) return DetectedLayout.NINTENDO
        return null
    }

    /**
     * Detects [device], or the first connected physical pad when [device] is null.
     */
    fun detectFor(device: InputDevice?): DetectionResult {
        if (device != null) return detectFromDevice(device)
        val firstPad = InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull { InputDevice.getDevice(it) }
            .firstOrNull { it.isPhysicalGamepad() }
            ?: return DetectionResult(null, null, null, DetectionSource.UNKNOWN)
        return detectFromDevice(firstPad)
    }

    fun isAnyGamepadConnected(): Boolean =
        InputDevice.getDeviceIds().any { InputDevice.getDevice(it)?.isPhysicalGamepad() == true }

    /**
     * Resolves the pad lettering every surface draws glyphs against. [controllerLayout] is the
     * user's Controller Layout preference and outranks [detected]; pass the live detection result
     * so a surface that already holds one does not probe the input devices again.
     */
    fun isNintendoLayout(controllerLayout: String, detected: DetectedLayout?): Boolean =
        when (controllerLayout) {
            "nintendo" -> true
            "xbox" -> false
            else -> detected == DetectedLayout.NINTENDO
        }
}
