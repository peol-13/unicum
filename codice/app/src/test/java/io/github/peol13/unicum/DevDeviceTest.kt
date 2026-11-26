package io.github.peol13.unicum

import io.github.peol13.unicum.data.DevDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevDeviceTest {
    @Test
    fun recognisesTheAndroidStudioEmulator() {
        // Valori dell'emulatore unicum_api35
        assertTrue(DevDevice.looksLikeEmulator(
            "google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:user/release-keys",
            "sdk_gphone64_x86_64", "ranchu", "sdk_gphone64_x86_64"
        ))
        assertTrue(DevDevice.looksLikeEmulator("generic/sdk/generic:8.0.0/OSR1/1:userdebug/test-keys", "Android SDK built for x86", "goldfish", "sdk"))
    }

    @Test
    fun realPhonesAreCounted() {
        assertFalse(DevDevice.looksLikeEmulator(
            "samsung/e3qxeea/e3q:15/AP3A.240905.015.A2/S928BXXS4BXK3:user/release-keys",
            "SM-S928B", "qcom", "e3qxeea"
        ))
        assertFalse(DevDevice.looksLikeEmulator(
            "google/husky/husky:15/AP4A.250105.002/12701944:user/release-keys", "Pixel 8 Pro", "husky", "husky"
        ))
    }
}
