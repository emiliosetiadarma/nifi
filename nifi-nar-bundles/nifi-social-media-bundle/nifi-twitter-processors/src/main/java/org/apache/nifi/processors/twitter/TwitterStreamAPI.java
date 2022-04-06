package org.apache.nifi.processors.twitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.TwitterCredentials;
import com.twitter.clientlib.api.TwitterApi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TwitterStreamAPI{
    public enum Endpoint {
        SAMPLE,
        SEARCH
    }
    public static String SAMPLE_ENDPOINT = "SAMPLE_ENDPOINT";
    public static String SEARCH_ENDPOINT = "SEARCH_ENDPOINT";

    private static final String BEARER_TOKEN = "BEARER_TOKEN";
    private static final String SAMPLE_PATH = "/2/tweets/sample/stream";
    private static final String SEARCH_PATH = "/2/tweets/search/stream";

    private TwitterCredentials creds;
    private TwitterApi api;
    private Endpoint endpoint;


    private TwitterStreamQueuer queuer;
    private BlockingQueue<String> queue;
    private Set<String> tweetFields;
    private volatile boolean exit;

    private InputStream stream;

    public TwitterStreamAPI() {
        tweetFields = new HashSet<>();
        tweetFields.add("author_id");
        tweetFields.add("id");
        tweetFields.add("created_at");

        queue = new LinkedBlockingQueue<>();
    }

    public void initializeApi(final Map<String, String> env) {
        System.out.println("Initializing API");
        creds = new TwitterCredentials();
        creds.setBearerToken(env.get(BEARER_TOKEN));

        api = new TwitterApi(creds);
    }

    public void setEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void deleteEndpoint() {
        this.endpoint = null;
    }


    public String getTweet() {
        if (stream == null) {
            System.err.println("Stream is null, no tweets to fetch");
            return null;
        }
        if (queuer == null || !queuer.isAlive()) {
            System.err.println("No tweet queuer thread running, cannot consume tweets");
            return null;
        }
        if (queue.isEmpty()) {
            System.out.println("Queue is empty, no Tweet to return");
            return null;
        }

        String tweet = queue.poll();

        return tweet;
    }

    public void startStream() {
        System.out.println("Starting stream");
        assert endpoint != null;

        try {
            if (endpoint == Endpoint.SAMPLE) {
                stream = api.sampleStream(null, tweetFields, null , null, null, null, null);
            } else if (endpoint == Endpoint.SEARCH) {
                stream = api.searchStream(null, tweetFields, null , null, null, null, null);
            } else {
                System.err.println("Endpoint is an invalid value.");
            }
        } catch (ApiException e) {
            System.err.println("Ran into API exception while starting the stream");
        }

        // set exited thread status to false
        exit = false;

        // wipe out old tweets and will fill with newer stream
        emptyQueue();

        // start the Queuer
        queuer = new TwitterStreamQueuer();
        queuer.start();
    }

    public void stopStream() {
        System.out.println("Stopping Stream");
        stream = null;

        exit = true;
    }

    public void initializeQueue() {
        queue = new LinkedBlockingQueue<>(1000);
    }

    public void emptyQueue() {
        while (!queue.isEmpty()) {
            queue.poll();
        }
    }

    public void cleanUp() {
        stopStream();
        emptyQueue();

        creds = null;
        api = null;
    }

    private class TwitterStreamQueuer extends Thread {
        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                for (String line = reader.readLine(); line != null ; line = reader.readLine()) {
                    if (exit) {
                        break;
                    }
                    JsonNode json = new ObjectMapper().readTree(line);
                    json = json.path("data");
                    System.out.println(json);
                    System.out.println(line);
                    queue.offer(line);
                    System.out.println("");
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
            System.out.println("exiting tread");
        }
    }

}
