package de.danoeh.antennapod.parser.feed;

import de.danoeh.antennapod.parser.feed.util.TypeGetter;
import org.apache.commons.io.input.XmlStreamReader;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;

public class FeedHandler {
    private static final int PREVIEW_TEXT_LENGTH = 200;

    public FeedHandlerResult parseFeed(Feed feed) throws SAXException, IOException,
            ParserConfigurationException, UnsupportedFeedtypeException {
        TypeGetter tg = new TypeGetter();
        TypeGetter.Type type = tg.getType(feed);
        SyndHandler handler = new SyndHandler(feed, type);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();
        File file = new File(feed.getLocalFileUrl());
        Reader inputStreamReader = new XmlStreamReader(file);
        InputSource inputSource = new InputSource(inputStreamReader);

        saxParser.parse(inputSource, handler);
        inputStreamReader.close();
        if (handler.state.feed.getItems() != null) {
            for (FeedItem item : handler.state.feed.getItems()) {
                if (item.getPreviewText() != null) {
                    String previewText = Jsoup.parse(item.getPreviewText()).text();
                    item.setPreviewText(StringUtils.isBlank(previewText)
                            ? null : StringUtils.left(previewText, PREVIEW_TEXT_LENGTH));
                }
            }
        }
        return new FeedHandlerResult(handler.state.feed, handler.state.alternateUrls, handler.state.redirectUrl);
    }
}
