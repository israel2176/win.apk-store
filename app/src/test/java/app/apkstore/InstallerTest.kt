package app.apkstore

import android.app.Application
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import app.apkstore.install.Installer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** ההתקנה: שם ואייקון נמסרים למערכת; במצב מתקין חבילות - Google Play כמתקין רשום */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun installAndGetSession(): PackageInstaller.SessionInfo {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        val apk = File(app.cacheDir, "t.apk").apply { writeBytes(ByteArray(64)) }
        runBlocking { Installer.install(app, apk.path, false, "com.example.t", "Example", null) }
        return app.packageManager.packageInstaller.allSessions.last()
    }

    @Test fun regularPhone() {
        assertFalse(Installer.isPackageInstaller(app))
        val s = installAndGetSession()
        println("regular: label=${s.appLabel} pkg=${s.appPackageName} installer=${s.installerPackageName}")
        assertEquals("Example", s.appLabel)
        assertEquals("com.example.t", s.appPackageName)
        assertTrue("not reported as Play", s.installerPackageName != "com.android.vending")
    }

    @Test fun packageInstallerMode() {
        shadowOf(app).grantPermissions(android.Manifest.permission.INSTALL_PACKAGES)
        assertTrue(Installer.isPackageInstaller(app))
        val s = installAndGetSession()
        println("privileged: label=${s.appLabel} installer=${s.installerPackageName} source=${s.packageSource}")
        assertEquals("Example", s.appLabel)
        assertEquals("com.android.vending", s.installerPackageName)
        // packageSource (PACKAGE_SOURCE_STORE) נשלח בפועל, אבל סביבת הבדיקה (Robolectric) לא שומרת אותו - לכן לא נבדק כאן
    }
}
