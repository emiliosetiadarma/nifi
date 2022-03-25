package org.apache.nifi.processors.twitter;

import com.fasterxml.jackson.databind.util.JSONPObject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.TweetsStreamListenersExecutor;
import com.twitter.clientlib.TwitterCredentials;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.model.StreamingTweet;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.schema.access.SchemaNotFoundException;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.WriteResult;
import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@SupportsBatching
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@Tags({"twitter", "tweets", "social media", "status", "json", "Record"})
@CapabilityDescription("Streams tweets from Twitter's streaming API v2.")
@WritesAttribute(attribute = "mime.type", description = "Sets mime type to application/json")
public class ConsumeTwitterRecord extends AbstractProcessor {

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

    static final PropertyDescriptor RECORD_WRITER = new PropertyDescriptor.Builder()
            .name("record-writer")
            .displayName("Record Writer")
            .description("The Record Writer to use in order to serialize the Tweets to the output FlowFile")
            .identifiesControllerService(RecordSetWriterFactory.class)
            .expressionLanguageSupported(ExpressionLanguageScope.NONE)
            .required(true)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("All tweets will be routed to this relationship.")
            .build();

    private List<PropertyDescriptor> descriptors;
    private Set<Relationship> relationships;

    private static TwitterApi api;
    private TwitterCredentials creds;

    private volatile BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(5000);

    interface TweetWriter {
        void beginListing() throws SchemaNotFoundException, IOException;
        void addToListing(final String tweet) throws IOException;
        void finishListing(final String endpointName) throws IOException;
        void finishListingExceptionally(final Exception cause);
        boolean isCheckpoint();
    }

    static class RecordTweetWriter implements TweetWriter {
        private static RecordSchema RECORD_SCHEMA;

        private static final String ID = "id";
        private static final String TEXT ="text";
        private static final String AUTHOR_ID = "author_id";
        private static final String CREATED_AT = "created_at";
        static {
            final List<RecordField> fields = new ArrayList<>();
            fields.add(new RecordField(ID, RecordFieldType.STRING.getDataType(), false));
            fields.add(new RecordField(TEXT, RecordFieldType.STRING.getDataType(), false));
            fields.add(new RecordField(AUTHOR_ID, RecordFieldType.STRING.getDataType(), false));
            fields.add(new RecordField(CREATED_AT, RecordFieldType.STRING.getDataType(), false));
            RECORD_SCHEMA = new SimpleRecordSchema(fields);
        }

        private final ProcessSession session;
        private final RecordSetWriterFactory writerFactory;
        private final ComponentLog logger;
        private RecordSetWriter recordWriter;
        private FlowFile flowFile;

        public RecordTweetWriter(final ProcessSession session, final RecordSetWriterFactory writerFactory, final ComponentLog logger) {
            this.session = session;
            this.writerFactory = writerFactory;
            this.logger = logger;
        }

        private Record createRecordForListing(String tweet) {
            JsonObject tweetJson = new Gson().fromJson(tweet, JsonObject.class);
            assert tweetJson.isJsonObject();

            final Map<String, Object> values = new HashMap<>();
            values.put(ID, tweetJson.get(ID).getAsString());
            values.put(TEXT, tweetJson.get(TEXT).getAsString());
            values.put(AUTHOR_ID, tweetJson.get(AUTHOR_ID).getAsString());
//            values.put(CREATED_AT);

            return new MapRecord(RECORD_SCHEMA, values);
        }

        @Override
        public void beginListing() throws SchemaNotFoundException, IOException {
            flowFile = session.create();

            final OutputStream out = session.write(flowFile);
            recordWriter = writerFactory.createWriter(logger, RECORD_SCHEMA, out, flowFile);
            recordWriter.beginRecordSet();
        }

        @Override
        public void addToListing(final String tweet) throws IOException {
            Record record = createRecordForListing(tweet);
            recordWriter.write(record);
        }

        @Override
        public void finishListing(final String transitUri) throws IOException {
            final WriteResult writeResult = recordWriter.finishRecordSet();
            recordWriter.close();

            if (writeResult.getRecordCount() == 0) {
                session.remove(flowFile);
            } else {
                final Map<String, String> attributes = new HashMap<>(writeResult.getAttributes());
                attributes.put("record.count", String.valueOf(writeResult.getRecordCount()));
                attributes.put(CoreAttributes.MIME_TYPE.key(), "application/json");
                attributes.put(CoreAttributes.FILENAME.key(), CoreAttributes.FILENAME.key() + ".json");
                flowFile = session.putAllAttributes(flowFile, attributes);

                session.transfer(flowFile, REL_SUCCESS);
                session.getProvenanceReporter().receive(flowFile, transitUri);
            }
        }

        @Override
        public void finishListingExceptionally(final Exception cause) {
            try {
                recordWriter.close();
            } catch (IOException e) {
                logger.error("Failed to write tweet as Records due to {}", new Object[] {e}, e);
            }

            session.remove(flowFile);
        }

        @Override
        public boolean isCheckpoint() {
            return false;
        }
    }

    @Override
    protected void init(ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(ENDPOINT);
        descriptors.add(BEARER_TOKEN);
        descriptors.add(RECORD_WRITER);

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
        if (api == null) {
            initializeClient(context);
        }

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
            org.apache.nifi.processors.twitter.ConsumeTwitterRecord.Responder responder = new org.apache.nifi.processors.twitter.ConsumeTwitterRecord.Responder();
            TweetsStreamListenersExecutor tsle = new TweetsStreamListenersExecutor(result);
            tsle.addListener(responder);
            tsle.executeListeners();
        }
        catch (ApiException e) {
            getLogger().error("Received error {}: {}.", new Object[]{e.getCode(), e.getResponseBody()});
        }
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        RecordTweetWriter writer;
        final RecordSetWriterFactory writerFactory = context.getProperty(RECORD_WRITER).asControllerService(RecordSetWriterFactory.class);
        if (writerFactory == null) {
            context.yield();
            return;
        } else {
            writer = new RecordTweetWriter(session, writerFactory, getLogger());
        }

        String tweet = messageQueue.poll();
        if (tweet == null) {
            context.yield();
            return;
        }

        try {
            int tweetCount = 0;
            writer.beginListing();
            do {
                getLogger().info("Tweet: {}", new Object[]{tweet});
                writer.addToListing(tweet);
                tweetCount++;
                tweet = messageQueue.poll();
            } while (!messageQueue.isEmpty());

            getLogger().info("Successfully listed {} new tweets; routing to success", tweetCount);

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

            writer.finishListing(transitUri);
            session.commitAsync();
        } catch (final Exception e) {
            getLogger().error("Failed to record tweets due to {}", new Object[]{e}, e);
            writer.finishListingExceptionally(e);
            session.rollback();
            context.yield();
            return;
        }
    }

    private class Responder implements com.twitter.clientlib.TweetsStreamListener {
        @Override
        public void actionOnTweetsStream(StreamingTweet streamingTweet) {
            Gson gson = new Gson();

            final String tweet = gson.toJson(streamingTweet.getData());

            final DateTimeFormatter formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;
            final String formattedCreatedAtString = formatter.format(streamingTweet.getData().getCreatedAt());
            JsonObject jsonObj = JsonParser.parseString(tweet).getAsJsonObject();
            jsonObj.remove("created_at");
            jsonObj.addProperty("created_at", formattedCreatedAtString);

            final String formattedTweetJson = gson.toJson(jsonObj);

            messageQueue.add(formattedTweetJson);
        }
    }
}
