package app.apkstore

import app.apkstore.data.DlState
import app.apkstore.data.InstalledApp
import app.apkstore.ui.Act
import app.apkstore.ui.actOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** איזה כפתור מוצג בכל מצב: הורדה → התקנה → פתיחה, ועדכון כשיש גרסה חדשה */
class ActTest {
    private val v527 = InstalledApp("5.27.5.0", 1030750)
    private fun ready(v: String?) = DlState.Ready("/x.apk", false, v)

    @Test fun notInstalled() {
        assertEquals(Act.Download, actOf(null, null))
        assertEquals(Act.Install, actOf(ready("5.28.0.0"), null))
        assertEquals(Act.Failed, actOf(DlState.Failed("x"), null))
        assertEquals(Act.Progress, actOf(DlState.Downloading(0.5f), null))
        assertEquals(Act.NetFree, actOf(DlState.NotChecked("u"), null))
    }

    @Test fun installed() {
        assertEquals("installed, nothing newer", Act.Open, actOf(null, v527))
        assertEquals("installed, APKPure newer", Act.UpdateDownload, actOf(null, v527, updateAvailable = true))
        assertEquals("downloaded the same version that is installed", Act.Open, actOf(ready("5.27.5.0"), v527))
        assertEquals("downloaded an older file", Act.Open, actOf(ready("5.20.0"), v527))
        assertEquals("downloaded a newer file", Act.UpdateInstall, actOf(ready("5.28.0.0"), v527))
        assertEquals("unknown version - offer to install", Act.UpdateInstall, actOf(ready(null), v527))
        assertEquals("failed but installed - still open", Act.Open, actOf(DlState.Failed("x"), v527))
    }
}
