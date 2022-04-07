package org.apache.nifi.processors.twitter;

import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.TwitterCredentials;
import com.twitter.clientlib.api.TwitterApi;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TwitterStreamAPI {
    public static final String SEARCH_ENDPOINT = "Search Endpoint";
    public static final String SAMPLE_ENDPOINT = "Sample Endpoint";
    public static final String SAMPLE_PATH = "/2/tweets/sample/stream";
    public static final String SEARCH_PATH = "/2/tweets/search/stream";

    private static final String BEARER_TOKEN_PROPERTY_NAME = "Bearer Token";

    private final BlockingQueue<String> queue;
    private final ComponentLog logger;

    private final ExecutorService executorService;

    private final Set<String> tweetFields;
    private final TwitterApi api;
    private InputStream stream;

    public TwitterStreamAPI(final ProcessContext context, final BlockingQueue<String> queue, final ComponentLog logger) {
        assert context != null;
        assert queue != null;
        assert logger != null;

        this.queue = queue;
        this.logger = logger;
        this.tweetFields = new HashSet<>(Arrays.asList("author_id", "id", "created_at", "text"));

        TwitterCredentials creds = new TwitterCredentials();
        creds.setBearerToken(context.getProperty(BEARER_TOKEN_PROPERTY_NAME).getValue());
        api = new TwitterApi(creds);

        this.executorService = Executors.newSingleThreadExecutor();
    }

    public String getBasePath() {
        return api.getApiClient().getBasePath();
    }

    /**
     * This method would be called when we would like the stream to get started. This method will spin off a thread that
     * will continue to queue tweets on to the given queue passed in the constructor. The thread will continue
     * to run until {@code stop} is called.
     * @param endpoint {@code TwitterStreamAPI.SAMPLE_ENDPOINT} or {@code TwitterStreamAPI.SEARCH_ENDPOINT}
     */
    public void start(final String endpoint) {
        try {
            if (endpoint.equals(SAMPLE_ENDPOINT)) {
                stream = api.sampleStream(null, tweetFields, null, null, null, null, null);
            } else if (endpoint.equals(SEARCH_ENDPOINT)) {
                stream = api.searchStream(null, tweetFields, null, null, null, null, null);
            } else {
                throw new AssertionError("Endpoint was invalid value: " + endpoint);
            }
        } catch (ApiException e) {
            logger.error("Received error {}: {}", new Object[]{e.getCode(), e.getMessage()});
        }

        executorService.execute(new TweetQueuer());
    }

    /**
     * This method would be called when we would like the stream to get stopped. The stream will be closed and the
     * executorService will be shut down. If it fails to shutdown, then it will be forcefully terminated.
     */
    public void stop() {
        try {
            stream.close();
        } catch (IOException e) {
            logger.error("IOException occurred while closing stream: {}", new Object[] {e.getMessage()});
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            logger.error("InterruptedException occurred while waiting for executor service to shut down: {}", new Object[] {e.getMessage()});
        }
    }

    private class TweetQueuer implements Runnable {
        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String tweetLine = reader.readLine();
                while (tweetLine != null) {
                    queue.offer(tweetLine);
                }
            } catch (IOException e) {
                logger.error("IOException occurred in TweetQueuer: {}", new Object[] {e.getMessage()});
            }
        }
    }

}