package de.danoeh.antennapod.storage.importexport;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SettingsExporterTest {

    @Test
    public void testImportMapsDefaultPreferencesOfOtherPackage() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String json = "{\"version\":1,\"preferences\":{"
                + "\"de.danoeh.antennapod.debug_preferences\":{"
                + "\"prefTheme\":{\"type\":\"string\",\"value\":\"1\"},"
                + "\"prefEpisodePreview\":{\"type\":\"boolean\",\"value\":true},"
                + "\"prefFastForwardSecs\":{\"type\":\"int\",\"value\":45},"
                + "\"prefPlaybackSpeed\":{\"type\":\"float\",\"value\":1.5},"
                + "\"prefDataFolder\":{\"type\":\"string\",\"value\":\"/old/path\"},"
                + "\"prefPlaybackSpeedArray\":{\"type\":\"string_set\",\"value\":[\"1.00\",\"1.50\"]}},"
                + "\"SwipeActionsPrefs\":{\"PrefSwipeActionsQueue\":"
                + "{\"type\":\"string\",\"value\":\"REMOVE_FROM_QUEUE,START_DOWNLOAD\"}},"
                + "\"gpodder.net\":{\"de.danoeh.antennapod.preferences.gpoddernet.password\":"
                + "{\"type\":\"string\",\"value\":\"secret\"}}}}";

        SettingsExporter.applyImport(context, new JSONObject(json));

        SharedPreferences prefs = context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        assertEquals("1", prefs.getString("prefTheme", null));
        assertTrue(prefs.getBoolean("prefEpisodePreview", false));
        assertEquals(45, prefs.getInt("prefFastForwardSecs", 0));
        assertEquals(1.5f, prefs.getFloat("prefPlaybackSpeed", 0), 0.0001f);
        assertFalse(prefs.contains("prefDataFolder"));
        assertEquals(new HashSet<>(Arrays.asList("1.00", "1.50")),
                prefs.getStringSet("prefPlaybackSpeedArray", null));
        assertEquals("REMOVE_FROM_QUEUE,START_DOWNLOAD", context.getSharedPreferences("SwipeActionsPrefs",
                Context.MODE_PRIVATE).getString("PrefSwipeActionsQueue", null));
        assertFalse(context.getSharedPreferences("gpodder.net", Context.MODE_PRIVATE)
                .contains("de.danoeh.antennapod.preferences.gpoddernet.password"));
    }

    @Test
    public void testExportAndImportRoundTrip() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String defaultName = context.getPackageName() + "_preferences";
        context.getSharedPreferences(defaultName, Context.MODE_PRIVATE).edit()
                .putBoolean("prefEpisodePreview", true)
                .putLong("prefSomeLong", 1234567890123L)
                .commit();

        JSONObject export = SettingsExporter.createExport(context);

        context.getSharedPreferences(defaultName, Context.MODE_PRIVATE).edit().clear().commit();
        SettingsExporter.applyImport(context, export);

        SharedPreferences prefs = context.getSharedPreferences(defaultName, Context.MODE_PRIVATE);
        assertTrue(prefs.getBoolean("prefEpisodePreview", false));
        assertEquals(1234567890123L, prefs.getLong("prefSomeLong", 0));
    }
}
