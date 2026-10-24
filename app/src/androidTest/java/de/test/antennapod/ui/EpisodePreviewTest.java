package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.espresso.util.TreeIterables;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.EspressoTestUtils;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static org.hamcrest.CoreMatchers.allOf;

@RunWith(AndroidJUnit4.class)
public class EpisodePreviewTest {
    private static final String PREVIEW = "Short preview of the first episode";
    private static final String TAG = "DiagCapture";
    private static final int LOGCAT_CHUNK = 3000;

    private UITestUtils uiTestUtils;
    private Feed feed;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        uiTestUtils = new UITestUtils(context);
        uiTestUtils.setup();
        uiTestUtils.addHostedFeedData();
        feed = uiTestUtils.hostedFeeds.get(0);
        for (FeedItem item : feed.getItems()) {
            item.setPreviewText(PREVIEW + " " + item.getTitle());
        }
        uiTestUtils.addLocalFeedData(false);
        EspressoTestUtils.setLaunchScreen("" + feed.getId());
    }

    @After
    public void tearDown() throws Exception {
        uiTestUtils.tearDown();
    }

    @Test
    public void testPreviewShownWhenEnabled() {
        setPreviewEnabled(true);
        activityRule.launchActivity(new Intent());
        try {
            FeedItem item = feed.getItemAtIndex(0);
            onView(isRoot()).perform(waitForView(withText(item.getTitle()), 3000));
            onView(allOf(withId(R.id.txtvPreview), withText(PREVIEW + " " + item.getTitle())))
                    .check(matches(isDisplayed()));
        } catch (Throwable t) {
            captureDiagnostics("shown-failure");
            throw t;
        }
        captureDiagnostics("shown-after-pass");
    }

    @Test
    public void testPreviewHiddenWhenDisabled() {
        setPreviewEnabled(false);
        activityRule.launchActivity(new Intent());
        try {
            FeedItem item = feed.getItemAtIndex(0);
            onView(isRoot()).perform(waitForView(withText(item.getTitle()), 3000));
            onView(withText(PREVIEW + " " + item.getTitle())).check(doesNotExist());
        } catch (Throwable t) {
            captureDiagnostics("hidden-failure");
            throw t;
        }
    }

    private void setPreviewEnabled(boolean enabled) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(UserPreferences.PREF_EPISODE_PREVIEW, enabled).commit();
    }

    /**
     * Diagnostic capture (temporary, diagnostic branch only). Two channels:
     * 1. logcat (primary): the full visible view tree (class, id, visibility, bounds, text)
     *    as DIAGTXT chunks, plus a downscaled JPEG of the root view as base64 DIAGIMG chunks.
     *    On test failure CI dumps logcat into the test-report artifact, so this channel
     *    needs no workflow change.
     * 2. files/diag/ on the emulator (secondary, for future artifact pulls): same tree as
     *    a .txt and the full-size screenshot as a .png. May fail on read-only storage;
     *    failures are logged and ignored.
     */
    private void captureDiagnostics(String name) {
        try {
            final StringBuilder dump = new StringBuilder();
            final Bitmap[] bitmap = new Bitmap[1];
            onView(isRoot()).perform(new ViewAction() {
                @Override
                public Matcher<View> getConstraints() {
                    return isRoot();
                }

                @Override
                public String getDescription() {
                    return "dump view tree and capture screenshot";
                }

                @Override
                public void perform(UiController uiController, View view) {
                    for (View child : TreeIterables.breadthFirstViewTraversal(view)) {
                        dump.append(describe(child)).append('\n');
                    }
                    bitmap[0] = Bitmap.createBitmap(Math.max(1, view.getWidth()),
                            Math.max(1, view.getHeight()), Bitmap.Config.ARGB_8888);
                    view.draw(new Canvas(bitmap[0]));
                }
            });
            String text = dump.toString();
            logChunked("DIAGTXT", name, text);
            logImage(name, bitmap[0]);
            writeToDevice(name, text, bitmap[0]);
        } catch (Exception e) {
            Log.e(TAG, "diagnostic capture failed for " + name, e);
        }
    }

    private static void logImage(String name, Bitmap bitmap) {
        try {
            int targetWidth = 270;
            Bitmap source = bitmap;
            if (bitmap.getWidth() > targetWidth) {
                int targetHeight = bitmap.getHeight() * targetWidth / bitmap.getWidth();
                source = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
            }
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            source.compress(Bitmap.CompressFormat.JPEG, 50, jpeg);
            logChunked("DIAGIMG", name,
                    Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP));
        } catch (Exception e) {
            Log.e(TAG, "image encode failed for " + name, e);
        }
    }

    private static void logChunked(String kind, String name, String payload) {
        int chunks = (payload.length() + LOGCAT_CHUNK - 1) / LOGCAT_CHUNK;
        Log.e(TAG, kind + "_BEGIN " + name + " chunks=" + chunks + " len=" + payload.length());
        for (int seq = 0; seq < chunks; seq++) {
            int end = Math.min(payload.length(), (seq + 1) * LOGCAT_CHUNK);
            Log.e(TAG, kind + " " + name + " " + seq + "/" + chunks + " "
                    + payload.substring(seq * LOGCAT_CHUNK, end));
        }
        Log.e(TAG, kind + "_END " + name);
    }

    private static void writeToDevice(String name, String text, Bitmap bitmap) {
        try {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            File dir = new File(context.getExternalFilesDir(null), "diag");
            dir.mkdirs();
            writeBytes(new File(dir, name + ".txt"), text.getBytes(StandardCharsets.UTF_8));
            FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"));
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
        } catch (Exception e) {
            Log.e(TAG, "device file write failed for " + name, e);
        }
    }

    private static String describe(View view) {
        String id;
        try {
            id = view.getResources().getResourceEntryName(view.getId());
        } catch (Exception e) {
            id = String.valueOf(view.getId());
        }
        String text = view instanceof TextView ? String.valueOf(((TextView) view).getText()) : "";
        return view.getClass().getSimpleName() + " id=" + id
                + " visibility=" + view.getVisibility()
                + " pos=" + view.getLeft() + "," + view.getTop()
                + " size=" + view.getWidth() + "x" + view.getHeight()
                + " text=\"" + text + "\"";
    }

    private static void writeBytes(File file, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        out.write(data);
        out.close();
    }
}
