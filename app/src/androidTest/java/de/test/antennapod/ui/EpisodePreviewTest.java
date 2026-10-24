package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import androidx.preference.PreferenceManager;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.EspressoTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.startsWith;

@RunWith(AndroidJUnit4.class)
public class EpisodePreviewTest {
    private static final String PREVIEW = "Short preview of the first episode";
    private static final long LIST_LOAD_TIMEOUT_MS = 10000;

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
        // Give the items distinct, spaced publication dates so the newest-first
        // display order is deterministic. UITestUtils seeds all items with new Date()
        // within milliseconds, which made the order unstable between runs.
        List<FeedItem> items = feed.getItems();
        long now = System.currentTimeMillis();
        for (int i = 0; i < items.size(); i++) {
            FeedItem item = items.get(i);
            item.setPubDate(new Date(now - (items.size() - i) * 60_000L));
            item.setPreviewText(PREVIEW + " " + item.getTitle());
            DBWriter.setFeedItem(item, false).get();
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
        FeedItem item = feed.getItemAtIndex(0);
        scrollEpisodeOnScreen(item);
        onView(allOf(withId(R.id.txtvPreview), withText(PREVIEW + " " + item.getTitle())))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testPreviewHiddenWhenDisabled() {
        setPreviewEnabled(false);
        activityRule.launchActivity(new Intent());
        FeedItem item = feed.getItemAtIndex(0);
        scrollEpisodeOnScreen(item);
        onView(allOf(withId(R.id.txtvPreview), withText(PREVIEW + " " + item.getTitle())))
                .check(doesNotExist());
    }

    /**
     * Scrolls the target episode's row on screen and returns only once the row is actually
     * displayed. RecyclerViewActions.scrollTo just calls RecyclerView.scrollToPosition and
     * waits for the main thread to idle - it neither waits for the layout pass that applies
     * the scroll nor verifies visibility, so a single scroll-then-check can observe the row
     * attached but still outside the viewport on a slow emulator. Repeating the scroll plus
     * a displayed-check until the row is genuinely visible removes that race without
     * weakening the test: if the row never appears, this fails loudly after the deadline.
     */
    private void scrollEpisodeOnScreen(FeedItem item) {
        onView(isRoot()).perform(waitForView(
                allOf(withId(R.id.txtvTitle), withText(startsWith("Feed "))), LIST_LOAD_TIMEOUT_MS));
        long deadline = System.currentTimeMillis() + LIST_LOAD_TIMEOUT_MS;
        while (true) {
            onView(withId(R.id.recyclerView))
                    .perform(RecyclerViewActions.scrollTo(hasDescendant(withText(item.getTitle()))));
            try {
                onView(allOf(withId(R.id.txtvTitle), withText(item.getTitle()), isDisplayed()))
                        .check(matches(isDisplayed()));
                return;
            } catch (Throwable t) {
                if (System.currentTimeMillis() > deadline) {
                    throw new AssertionError("Episode row did not become displayed after scrolling: "
                            + item.getTitle(), t);
                }
            }
        }
    }

    private void setPreviewEnabled(boolean enabled) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(UserPreferences.PREF_EPISODE_PREVIEW, enabled).commit();
    }
}
