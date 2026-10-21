package de.danoeh.antennapod.storage.importexport;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SettingsExporter {
    private static final int FORMAT_VERSION = 1;
    private static final String DEFAULT_PREFERENCES = "default";
    private static final String DEFAULT_PREFERENCES_SUFFIX = "_preferences";
    private static final Set<String> EXCLUDED_FILES = new HashSet<>(
            Arrays.asList("gpodder.net", "synchronization", "app_version"));
    private static final Set<String> EXCLUDED_KEYS = new HashSet<>(
            Arrays.asList("prefDataFolder", "prefAutomaticExportFolder"));

    private SettingsExporter() {
    }

    public static void exportToDocument(Uri uri, Context context) throws IOException {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (outputStream == null) {
                throw new IOException("Unable to open " + uri);
            }
            outputStream.write(createExport(context).toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public static void importFromDocument(Uri uri, Context context) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Unable to open " + uri);
            }
            applyImport(context, new JSONObject(IOUtils.toString(inputStream, StandardCharsets.UTF_8)));
        } catch (JSONException e) {
            throw new IOException("Not a valid settings file", e);
        }
    }

    public static JSONObject createExport(Context context) throws JSONException {
        JSONObject files = new JSONObject();
        File[] prefFiles = new File(context.getApplicationInfo().dataDir, "shared_prefs").listFiles();
        if (prefFiles != null) {
            for (File prefFile : prefFiles) {
                String fileName = prefFile.getName();
                if (!fileName.endsWith(".xml")) {
                    continue;
                }
                String name = fileName.substring(0, fileName.length() - 4);
                if (EXCLUDED_FILES.contains(name)) {
                    continue;
                }
                SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
                files.put(name.equals(defaultName(context)) ? DEFAULT_PREFERENCES : name, toJson(prefs.getAll()));
            }
        }
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("preferences", files);
        return root;
    }

    public static void applyImport(Context context, JSONObject root) throws JSONException {
        JSONObject files = root.getJSONObject("preferences");
        Iterator<String> names = files.keys();
        while (names.hasNext()) {
            String name = names.next();
            String target = name;
            if (name.equals(DEFAULT_PREFERENCES) || name.endsWith(DEFAULT_PREFERENCES_SUFFIX)) {
                target = defaultName(context);
            }
            if (EXCLUDED_FILES.contains(target)) {
                continue;
            }
            SharedPreferences.Editor editor = context.getSharedPreferences(target, Context.MODE_PRIVATE).edit();
            JSONObject values = files.getJSONObject(name);
            Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (EXCLUDED_KEYS.contains(key)) {
                    continue;
                }
                JSONObject entry = values.getJSONObject(key);
                switch (entry.getString("type")) {
                    case "boolean":
                        editor.putBoolean(key, entry.getBoolean("value"));
                        break;
                    case "int":
                        editor.putInt(key, entry.getInt("value"));
                        break;
                    case "long":
                        editor.putLong(key, entry.getLong("value"));
                        break;
                    case "float":
                        editor.putFloat(key, (float) entry.getDouble("value"));
                        break;
                    case "string":
                        editor.putString(key, entry.getString("value"));
                        break;
                    case "string_set":
                        JSONArray array = entry.getJSONArray("value");
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < array.length(); i++) {
                            set.add(array.getString(i));
                        }
                        editor.putStringSet(key, set);
                        break;
                    default:
                        break;
                }
            }
            editor.commit();
        }
    }

    private static JSONObject toJson(Map<String, ?> values) throws JSONException {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> value : values.entrySet()) {
            if (EXCLUDED_KEYS.contains(value.getKey()) || value.getValue() == null) {
                continue;
            }
            JSONObject entry = new JSONObject();
            Object object = value.getValue();
            if (object instanceof Boolean) {
                entry.put("type", "boolean");
                entry.put("value", object);
            } else if (object instanceof Integer) {
                entry.put("type", "int");
                entry.put("value", object);
            } else if (object instanceof Long) {
                entry.put("type", "long");
                entry.put("value", object);
            } else if (object instanceof Float) {
                entry.put("type", "float");
                entry.put("value", ((Float) object).doubleValue());
            } else if (object instanceof Set) {
                entry.put("type", "string_set");
                JSONArray array = new JSONArray();
                for (Object item : (Set<?>) object) {
                    array.put(String.valueOf(item));
                }
                entry.put("value", array);
            } else {
                entry.put("type", "string");
                entry.put("value", String.valueOf(object));
            }
            result.put(value.getKey(), entry);
        }
        return result;
    }

    private static String defaultName(Context context) {
        return context.getPackageName() + DEFAULT_PREFERENCES_SUFFIX;
    }
}
