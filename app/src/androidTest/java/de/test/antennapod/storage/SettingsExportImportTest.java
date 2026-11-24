package de.test.antennapod.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

import de.danoeh.antennapod.storage.importexport.SettingsExporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SettingsExportImportTest {

    @Test
    public void testExportThenImportRestoresSettings() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        SharedPreferences swipePrefs = context.getSharedPreferences("SwipeActionsPrefs", Context.MODE_PRIVATE);
        SharedPreferences syncCredentials = context.getSharedPreferences("gpodder.net", Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("testExportBoolean", true)
                .putString("testExportString", "dark")
                .putInt("testExportInt", 45)
                .putFloat("testExportFloat", 1.5f)
                .putStringSet("testExportSet", new HashSet<>(Arrays.asList("1.00", "1.50")))
                .commit();
        swipePrefs.edit().putString("testExportSwipe", "REMOVE_FROM_QUEUE,START_DOWNLOAD").commit();
        syncCredentials.edit().putString("testExportPassword", "do-not-export").commit();

        File file = new File(context.getCacheDir(), "settings-export-test.json");
        SettingsExporter.exportToDocument(Uri.fromFile(file), context);
        String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("testExportBoolean"));
        assertTrue(content.contains("testExportSwipe"));
        assertFalse(content.contains("do-not-export"));

        prefs.edit().remove("testExportBoolean").remove("testExportString").remove("testExportInt")
                .remove("testExportFloat").remove("testExportSet").commit();
        swipePrefs.edit().remove("testExportSwipe").commit();
        assertFalse(prefs.contains("testExportBoolean"));

        SettingsExporter.importFromDocument(Uri.fromFile(file), context);

        assertTrue(prefs.getBoolean("testExportBoolean", false));
        assertEquals("dark", prefs.getString("testExportString", null));
        assertEquals(45, prefs.getInt("testExportInt", 0));
        assertEquals(1.5f, prefs.getFloat("testExportFloat", 0), 0.0001f);
        assertEquals(new HashSet<>(Arrays.asList("1.00", "1.50")), prefs.getStringSet("testExportSet", null));
        assertEquals("REMOVE_FROM_QUEUE,START_DOWNLOAD", swipePrefs.getString("testExportSwipe", null));

        prefs.edit().remove("testExportBoolean").remove("testExportString").remove("testExportInt")
                .remove("testExportFloat").remove("testExportSet").commit();
        swipePrefs.edit().remove("testExportSwipe").commit();
        syncCredentials.edit().remove("testExportPassword").commit();
        assertTrue(file.delete());
    }
}
