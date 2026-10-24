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
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.EspressoTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

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
        FeedItem item = feed.getItemAtIndex(0);
        onView(isRoot()).perform(waitForView(withText(item.getTitle()), 3000));
        onView(allOf(withId(R.id.txtvPreview), withText(PREVIEW + " " + item.getTitle())))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testPreviewHiddenWhenDisabled() {
        setPreviewEnabled(false);
        activityRule.launchActivity(new Intent());
        FeedItem item = feed.getItemAtIndex(0);
        onView(isRoot()).perform(waitForView(withText(item.getTitle()), 3000));
        onView(withText(PREVIEW + " " + item.getTitle())).check(doesNotExist());
    }

    private void setPreviewEnabled(boolean enabled) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(UserPreferences.PREF_EPISODE_PREVIEW, enabled).commit();
    }
}
