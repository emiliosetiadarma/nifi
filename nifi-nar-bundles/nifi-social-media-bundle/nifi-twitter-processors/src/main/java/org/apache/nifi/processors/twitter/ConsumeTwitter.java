package org.apache.nifi.processors.twitter;

import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.TwitterCredentials;
import com.twitter.clientlib.api.TwitterApi;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.serialization.RecordSetWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@SupportsBatching
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@Tags({"twitter", "tweets", "social media", "status", "json", "Record"})
@CapabilityDescription("Streams tweets from Twitter's streaming API v2.")
@WritesAttribute(attribute = "mime.type", description = "Sets mime type to application/json")
public class ConsumeTwitter extends AbstractProcessor {

    static final AllowableValue ENDPOINT_SAMPLE = new AllowableValue("Sample Endpoint", "Sample Endpoint", "The endpoint that provides a stream of about 1% of tweets in real-time");
    static final AllowableValue ENDPOINT_SEARCH = new AllowableValue("Search Endpoint", "Search Endpoint", "The endpoint that provides a stream of tweets that matches the rules you added to the stream. If rules are not configured, then the stream will be empty");
    static final String SAMPLE_PATH = "/2/tweets/sample/stream";
    static final String SEARCH_PATH = "/2/tweets/search/stream";

    public static final PropertyDescriptor ENDPOINT = new PropertyDescriptor.Builder()
            .name("Twitter Endpoint")
            .description("Specifies which endpoint tweets should be pulled from.")
            .required(true)
            .allowableValues(ENDPOINT_SAMPLE, ENDPOINT_SEARCH)
            .defaultValue(ENDPOINT_SAMPLE.getValue())
            .build();
    public static final PropertyDescriptor BEARER_TOKEN = new PropertyDescriptor.Builder()
            .name("Bearer Token")
            .description("The Bearer Token provided by Twitter.")
            .required(true)
            .sensitive(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("All tweets will be routed to this relationship.")
            .build();

    private List<PropertyDescriptor> descriptors;
    private Set<Relationship> relationships;

    private TwitterApi api;
    private TwitterCredentials creds;
    private TwitterListener listener;

    private volatile BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(5000);

    @Override
    protected void init(ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(ENDPOINT);
        descriptors.add(BEARER_TOKEN);

        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return this.descriptors;
    }


    /**
     * Initializes a client to communicate to Twitter API. Client is based on the resource from
     * https://github.com/twitterdev/twitter-api-java-sdk.
     * @param context The context that is passed onto the processor from the NiFi framework
     */
    private void initializeClient(final ProcessContext context) {
        creds = new TwitterCredentials();
        creds.setBearerToken(context.getProperty(BEARER_TOKEN).getValue());
        api = new TwitterApi(creds);
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        initializeClient(context);
        emptyQueue();

        final String endpointName = context.getProperty(ENDPOINT).getValue();
        final Set<String> tweetFields = new HashSet<>(Arrays.asList("author_id", "id", "created_at", "text"));

        // The responder will keep on adding tweets to the BlockingQueue
        InputStream result;
        try {
            if (ENDPOINT_SAMPLE.getValue().equals(endpointName)) {
                result = api.sampleStream(null, tweetFields, null, null, null, null, null);
            }
            else if (ENDPOINT_SEARCH.getValue().equals(endpointName)){
                result = api.searchStream(null, tweetFields, null, null, null, null, null);
            }
            else {
                throw new AssertionError("Endpoint was invalid value: " + endpointName);
            }
            listener = new TwitterListener(result, messageQueue, getLogger());
            listener.execute();
        }
        catch (ApiException e) {
            getLogger().error("Received error {}: {}.", new Object[]{e.getCode(), e.getResponseBody()});
        }
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        final String tweet = messageQueue.poll();
        if (tweet == null) {
            context.yield();
            return;
        }

        FlowFile flowFile = session.create();
        flowFile = session.write(flowFile, new OutputStreamCallback() {
            @Override
            public void process(OutputStream out) throws IOException {
                out.write(tweet.getBytes(StandardCharsets.UTF_8));
            }
        });

        final Map<String, String> attributes = new HashMap<>();
        attributes.put(CoreAttributes.MIME_TYPE.key(), "application/json");
        attributes.put(CoreAttributes.FILENAME.key(), flowFile.getAttribute(CoreAttributes.FILENAME.key()) + ".json");
        flowFile = session.putAllAttributes(flowFile, attributes);
        
        session.transfer(flowFile, REL_SUCCESS);
        final String endpointName = context.getProperty(ENDPOINT).getValue();
        String transitUri = api.getApiClient().getBasePath();
        if (ENDPOINT_SAMPLE.getValue().equals(endpointName)) {
            transitUri += SAMPLE_PATH;
        }
        else if (ENDPOINT_SEARCH.getValue().equals(endpointName)) {
            transitUri += SEARCH_PATH;
        }
        else {
            throw new AssertionError("Endpoint was invalid value: " + endpointName);
        }
        session.getProvenanceReporter().receive(flowFile, transitUri);
    }

    @OnStopped
    public void onStopped() {
        if (listener != null) {
            listener.stop();
        }
        api = null;
        emptyQueue();
    }

    private void emptyQueue() {
        while (!messageQueue.isEmpty()) {
            messageQueue.poll();
        }
    }
}
