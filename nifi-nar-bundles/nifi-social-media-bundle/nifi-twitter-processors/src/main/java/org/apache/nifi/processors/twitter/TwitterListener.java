package org.apache.nifi.processors.twitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.nifi.logging.ComponentLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TwitterListener {
    private final BlockingQueue<String> queue;
    private final BlockingQueue<String> externalQueue;
    private final InputStream stream;
    private final ComponentLog logger;

    private Queuer queuer;
    private Executor executor;
    private boolean exit;

    public TwitterListener(final InputStream stream, final BlockingQueue<String> externalQueue, final ComponentLog logger) {
        this.queue = new LinkedBlockingQueue<>(1000);
        this.externalQueue = externalQueue;
        this.stream = stream;
        this.logger = logger;
        this.exit = false;
    }

    public void execute() {
        if (this.stream == null) {
            logger.warn("Stream is null");
        } else if (this.queue == null) {
            logger.warn("Tweet Queue is null");
        } else {
            exit = false;

            queuer = new Queuer();
            executor = new Executor();

            queuer.start();
            executor.start();
        }
    }

    public void stop() {
        exit = true;
        try {
            queuer.join(1000L);
        } catch (InterruptedException e) {
            logger.error("Encountered error while stopping Queuer: {}", e.getMessage());
        }
        try {
            executor.join(1000L);
        } catch (InterruptedException e) {
            logger.error("Encountered error while stopping Executor: {}", e.getMessage());
        }
        try {
            stream.close();
        } catch (IOException e) {
            logger.error("Encountered error while stopping stream: {}", e.getMessage());
        }
    }

    private class Queuer extends Thread {
        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                String line = reader.readLine();
                while (!exit && line != null) {
                    JsonNode json = new ObjectMapper().readTree(line);
                    json = json.path("data");
                    queue.offer(json.toString());
                    line = reader.readLine();
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }

    private class Executor extends Thread {
        @Override
        public void run() {
            try {
                while (!exit) {
                    String tweet = queue.poll();
                    if (tweet == null) {
                        Thread.sleep(100L);
                    } else {
                        externalQueue.add(tweet);
                    }
                }
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }
    }

}