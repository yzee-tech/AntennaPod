package de.danoeh.antennapod;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

public class MissingDownloadsChecker {
    private static final String TAG = "MissingDownloadsChecker";
    private static final AtomicBoolean checked = new AtomicBoolean(false);

    private MissingDownloadsChecker() {
    }

    public static void checkInBackground(Context context) {
        if (checked.getAndSet(true)) {
            return;
        }
        Context appContext = context.getApplicationContext();
        new Thread(() -> check(appContext), TAG).start();
    }

    private static void check(Context context) {
        List<FeedItem> items = DBReader.getEpisodes(0, Integer.MAX_VALUE,
                new FeedItemFilter(FeedItemFilter.DOWNLOADED), SortOrder.DATE_NEW_OLD);
        List<FeedItem> missing = new ArrayList<>();
        for (FeedItem item : items) {
            FeedMedia media = item.getMedia();
            if (media == null || media.getLocalFileUrl() == null || item.getFeed() == null
                    || item.getFeed().isLocalFeed() || media.fileExists()) {
                continue;
            }
            if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(
                    new File(media.getLocalFileUrl())))) {
                continue;
            }
            media.setDownloaded(false, 0);
            media.setLocalFileUrl(null);
            try {
                DBWriter.setMediaDownloadInformation(media).get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "Could not update " + media.getDownloadUrl(), e);
                continue;
            }
            missing.add(item);
        }
        if (missing.isEmpty()) {
            return;
        }
        Log.i(TAG, "Downloading " + missing.size() + " episodes again because their files are missing");
        EventBus.getDefault().post(new FeedItemEvent(missing, false));
        for (FeedItem item : missing) {
            DownloadServiceInterface.get().downloadWithoutEnqueue(context, item);
        }
    }
}
