package org.apache.nifi.processors.twitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.TwitterCredentials;
import com.twitter.clientlib.api.TwitterApi;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TwitterStreamAPI {
    public enum Endpoint {
        SAMPLE,
        SEARCH
    }

    public static final String SAMPLE_PATH = "/2/tweets/sample/stream";
    public static final String SEARCH_PATH = "/2/tweets/search/stream";

    private static final String BEARER_TOKEN = "BEARER_TOKEN";

    private TwitterApi api;
    private Endpoint endpoint;
    private Set<String> tweetFields;

    private InputStream stream;
    private TwitterStreamQueuer queuer;
    private BlockingQueue<String> queue;
    private volatile boolean exit;

    private volatile boolean isStarted;

    private ComponentLog logger;

    public TwitterStreamAPI(final ComponentLog logger, final ProcessContext context) {
        tweetFields = new HashSet<>(Arrays.asList("author_id", "id", "created_at", "text"));

        queue = new LinkedBlockingQueue<>(1000);

        this.logger = logger;

        TwitterCredentials creds = new TwitterCredentials();
        creds.setBearerToken(context.getProperty(BEARER_TOKEN).getValue());

        api = new TwitterApi(creds);

        isStarted = false;
    }

    public String getBasePath() {
        return api.getApiClient().getBasePath();
    }

    public void setEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public String getTweet() {
        if (stream == null) {
//            logger.warn("Stream is null or has not started, no tweets to fetch");
            return null;
        }
        if (queue.isEmpty()) {
//            logger.warn("No tweet queued, will return null");
            return null;
        }
        if (queuer == null || !queuer.isAlive()) {
//            logger.warn("No queuer started, or queuer has finished, restart stream to fetch tweets");
            return null;
        }

        String tweet = queue.poll();

        return tweet;
    }

    public void emptyQueue() {
        while (!queue.isEmpty()) {
            queue.poll();
        }
    }

    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    public void startStream() {
        assert endpoint != null;

        if (isStarted) {
            return;
        }

        try {
            if (endpoint == Endpoint.SAMPLE) {
                stream = api.sampleStream(null, tweetFields, null , null, null, null, null);
            } else if (endpoint == Endpoint.SEARCH) {
                stream = api.searchStream(null, tweetFields, null , null, null, null, null);
            } else {
                logger.error("Endpoint is an invalid value.");
                return;
            }
        } catch (ApiException e) {
            logger.error("Received error {}: {}.", new Object[]{e.getCode(), e.getResponseBody()});
        }

        // set exited thread status to false
        exit = false;

        // start the Queuer
        queuer = new TwitterStreamQueuer();
        queuer.start();

        isStarted = true;
    }

    public void stopStream() {
        exit = true;
        try {
            queuer.join(10000L);
        } catch (InterruptedException e) {
            logger.error("Interrupted exception while waiting for queuer to terminate");
        }

        isStarted = false;
    }

    public boolean isStreamStarted() {
        return isStarted;
    }


    private class TwitterStreamQueuer extends Thread {
        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                String line = reader.readLine();
                while (!exit && line != null) {
                    JsonNode json = new ObjectMapper().readTree(line);
                    json = json.path("data");
                    queue.offer(json.toString());
                    logger.error(json.toString());
                    line = reader.readLine();
                }
            } catch (Exception e) {
                logger.warn(e.getMessage());
            }
        }
    }

}
