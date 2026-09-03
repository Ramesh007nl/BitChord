package com.music.bitchord

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TanTovCarManifestTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun devApkDeclaresTemplatedMediaSupport() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(
                PackageManager.GET_PERMISSIONS.toLong() or PackageManager.GET_SERVICES.toLong(),
            ),
        )
        assertTrue(
            packageInfo.requestedPermissions.orEmpty()
                .contains("androidx.car.app.MEDIA_TEMPLATES"),
        )
        assertTrue(
            packageInfo.services.orEmpty().any {
                it.name == "com.music.bitchord.car.TanTovCarAppService" && it.exported
            },
        )

        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )
        assertEquals(8, appInfo.metaData.getInt("androidx.car.app.minCarApiLevel"))
        assertTrue(appInfo.metaData.getInt("com.google.android.gms.car.application") != 0)
    }
}
