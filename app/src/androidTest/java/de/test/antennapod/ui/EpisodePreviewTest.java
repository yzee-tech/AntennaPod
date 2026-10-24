package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import androidx.preference.PreferenceManager;
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
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static org.hamcrest.CoreMatchers.allOf;

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
        FeedItem item = waitForNewestItemDisplayed();
        onView(allOf(withId(R.id.txtvPreview), withText(PREVIEW + " " + item.getTitle())))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testPreviewHiddenWhenDisabled() {
        setPreviewEnabled(false);
        activityRule.launchActivity(new Intent());
        FeedItem item = waitForNewestItemDisplayed();
        onView(allOf(withId(R.id.txtvPreview), withText(PREVIEW + " " + item.getTitle())))
                .check(doesNotExist());
    }

    /**
     * Waits until the list has loaded and the newest episode's row is actually displayed,
     * then returns that item. The newest item is what the preview assertions below run
     * against: setUp gives the items spaced publication dates, so the newest one is
     * deterministically the first row of the newest-first list and renders on screen
     * without scrolling.
     *
     * Deliberately no scrolling: espresso's scrollTo is only RecyclerView.scrollToPosition
     * plus one idle wait, and on this collapsing-toolbar screen (tiny emulator viewport,
     * large bottom padding for the speed-dial overlay) it deterministically stalled
     * part-way in CI - the target row was never laid out on API 36, and was left attached
     * but outside the visible rect on API 30. Preview visibility is a property of the
     * row binding, not of any particular row, so asserting on the always-visible first
     * row checks the same behavior without depending on an unreliable scroll.
     */
    private FeedItem waitForNewestItemDisplayed() {
        FeedItem item = feed.getItemAtIndex(feed.getItems().size() - 1);
        onView(isRoot()).perform(waitForView(
                allOf(withId(R.id.txtvTitle), withText(item.getTitle()), isDisplayed()),
                LIST_LOAD_TIMEOUT_MS));
        return item;
    }

    private void setPreviewEnabled(boolean enabled) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(UserPreferences.PREF_EPISODE_PREVIEW, enabled).commit();
    }
}
