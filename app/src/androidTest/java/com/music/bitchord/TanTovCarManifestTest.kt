package com.music.bitchord

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

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
        val descriptorRes = appInfo.metaData.getInt("com.google.android.gms.car.application")
        assertTrue(descriptorRes != 0)

        val capabilities = mutableSetOf<String>()
        context.resources.getXml(descriptorRes).use { descriptor ->
            while (descriptor.eventType != XmlPullParser.END_DOCUMENT) {
                if (descriptor.eventType == XmlPullParser.START_TAG && descriptor.name == "uses") {
                    descriptor.getAttributeValue(null, "name")?.let(capabilities::add)
                }
                descriptor.next()
            }
        }
        assertTrue(capabilities.contains("media"))
        assertTrue(capabilities.contains("template"))
        assertTrue(appInfo.metaData.getInt("androidx.car.app.TintableAttributionIcon") != 0)
    }
}
