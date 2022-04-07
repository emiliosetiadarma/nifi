package org.apache.nifi.processors.twitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import org.apache.nifi.schema.access.SchemaAccessStrategy;
import org.apache.nifi.schema.access.SchemaNotFoundException;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.serialization.WriteResult;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.schema.access.InferenceSchemaStrategy;
import org.apache.nifi.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

@SupportsBatching
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@Tags({"twitter", "tweets", "social media", "status", "json", "Record"})
@CapabilityDescription("Streams tweets from Twitter's streaming API v2.")
@WritesAttribute(attribute = "mime.type", description = "Sets mime type to format specified by Record Writer")
public class ConsumeTwitterRecord extends AbstractProcessor {

    static final AllowableValue ENDPOINT_SAMPLE = new AllowableValue("Sample Endpoint", "Sample Endpoint", "The endpoint that provides a stream of about 1% of tweets in real-time");
    static final AllowableValue ENDPOINT_SEARCH = new AllowableValue("Search Endpoint", "Search Endpoint", "The endpoint that provides a stream of tweets that matches the rules you added to the stream. If rules are not configured, then the stream will be empty");

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
    public static final PropertyDescriptor RECORD_WRITER = new PropertyDescriptor.Builder()
            .name("record-writer")
            .displayName("Record Writer")
            .description("The Record Writer to use in order to serialize the Tweets to the output FlowFile")
            .identifiesControllerService(RecordSetWriterFactory.class)
            .expressionLanguageSupported(ExpressionLanguageScope.NONE)
            .required(true)
            .build();
    public static final PropertyDescriptor MAX_TWEET_PER_RECORD = new PropertyDescriptor.Builder()
            .name("Max Tweets per Record")
            .description("The maximum number of tweets per record")
            .required(true)
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .defaultValue("10")
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("All tweets will be routed to this relationship.")
            .build();

    private List<PropertyDescriptor> descriptors;
    private Set<Relationship> relationships;

    private TwitterStreamAPI api;
    private static ObjectMapper mapper;

    private volatile LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(5000);

    private void emptyQueue() {
        while (!messageQueue.isEmpty()) {
            messageQueue.poll();
        }
    }

    interface TweetWriter {
        boolean hasSchema();
        void inferSchema(final String tweet) throws SchemaNotFoundException, IOException;
        void beginListing() throws SchemaNotFoundException, IOException;
        boolean addToListing(final String tweet) throws IOException;
        void finishListing(final String endpointName) throws IOException;
        void finishListingExceptionally(final Exception cause);
    }

    static class RecordTweetWriter implements TweetWriter {
        private final ProcessContext context;
        private final ProcessSession session;
        private final RecordSetWriterFactory writerFactory;
        private final ComponentLog logger;
        private RecordSetWriter recordWriter;
        private RecordSchema schema;
        private FlowFile flowFile;

        public RecordTweetWriter(final ProcessContext context, final ProcessSession session,
                                 final RecordSetWriterFactory writerFactory, final ComponentLog logger) {
            this.context = context;
            this.session = session;
            this.writerFactory = writerFactory;
            this.logger = logger;
        }

        private Record createRecordForListing(final String tweet) {
            if (schema == null) {
                logger.warn("Schema has not yet been inferred. Cannot create record for listing");
                return null;
            }

            final Map<String, Object> values = new HashMap<>();
            try {
                JsonNode tweetJson = mapper.readTree(tweet);

                for (String field: schema.getFieldNames()) {
                    values.put(field, tweetJson.get(field));
                }
            } catch (IOException e) {
                logger.warn("Reading JSON tree failed while creating Record for listing: {}", new Object[] {e.getMessage()});
                return null;
            }

            return new MapRecord(schema, values);
        }

        @Override
        public boolean hasSchema() {
            return schema != null;
        }

        @Override
        public void inferSchema(final String tweet) throws SchemaNotFoundException, IOException {
            JsonNode tweetJson;
            try {
                tweetJson = mapper.readTree(tweet).path("data");
            } catch (IOException e) {
                logger.warn("Reading JSON tree failed while inferring schema: {}", new Object[] {e.getMessage()});
                return;
            }

            SchemaAccessStrategy strategy = new InferenceSchemaStrategy();
            InputStream contentStream = new ByteArrayInputStream(tweetJson.toString().getBytes(StandardCharsets.UTF_8));

            schema = strategy.getSchema(null, contentStream, null);
            logger.info("Generated schema: ");
            for (String fieldName: schema.getFieldNames()) {
                logger.info(fieldName);
            }
        }

        @Override
        public void beginListing() throws SchemaNotFoundException, IOException {
            flowFile = session.create();
            final OutputStream out = session.write(flowFile);
            recordWriter = writerFactory.createWriter(logger, schema, out, flowFile);
            recordWriter.beginRecordSet();
        }

        @Override
        public boolean addToListing(final String tweet) throws IOException {
            Record record = createRecordForListing(tweet);
            if (record == null) {
                logger.error("Failed to create record to add to listing");
                return false;
            }

            recordWriter.write(record);
            return true;
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
                attributes.put(CoreAttributes.MIME_TYPE.key(), recordWriter.getMimeType());
                flowFile = session.putAllAttributes(flowFile, attributes);

                session.transfer(flowFile, REL_SUCCESS);
                session.getProvenanceReporter().receive(flowFile, transitUri);
            }
        }

        @Override
        public void finishListingExceptionally(final Exception cause) {
            logger.error("Error occurred during listing, finishing exceptionally: {}", new Object[] {cause.getMessage()}, cause);
            try {
                recordWriter.close();
            } catch (IOException e) {
                logger.error("Failed to close record writer due to {}", new Object[] {e}, e);
            }

            session.remove(flowFile);
        }
    }

    private RecordTweetWriter createWriter(final ProcessContext context, final ProcessSession session) {
        final RecordSetWriterFactory writerFactory = context.getProperty(RECORD_WRITER).asControllerService(RecordSetWriterFactory.class);
        if (writerFactory == null) {
            return null;
        } else {
            return new RecordTweetWriter(context, session, writerFactory, getLogger());
        }
    }

    private void processTweets(final RecordTweetWriter writer, final ProcessContext context, final ProcessSession session) {
        if (writer == null) {
            return;
        }

        String tweet;
        do {
            tweet = messageQueue.poll();
        } while ( !messageQueue.isEmpty() && StringUtils.isEmpty(tweet));

        try {
            writer.inferSchema(tweet);
        } catch (SchemaNotFoundException | IOException e) {
            getLogger().error("Error occurred while inferring schema: {}", new Object[] {e.getMessage()});
            context.yield();
            return;
        }

        try {
            int tweetCount = 0;
            writer.beginListing();
            do {
                if (writer.addToListing(tweet)) {
                    tweetCount++;
                }
                tweet = messageQueue.poll();
            } while (!messageQueue.isEmpty() && tweetCount < context.getProperty(MAX_TWEET_PER_RECORD).asInteger());

            getLogger().info("Successfully listed {} new tweets; routing to success", tweetCount);

            String transitUri = api.getBasePath();
            final String endpointName = context.getProperty(ENDPOINT).getValue();
            if (ENDPOINT_SAMPLE.getValue().equals(endpointName)) {
                transitUri += TwitterStreamAPI.SAMPLE_PATH;
            }
            else if (ENDPOINT_SEARCH.getValue().equals(endpointName)) {
                transitUri += TwitterStreamAPI.SEARCH_PATH;
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
            return;
        }
    }

    @Override
    protected void init(ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(ENDPOINT);
        descriptors.add(BEARER_TOKEN);
        descriptors.add(RECORD_WRITER);
        descriptors.add(MAX_TWEET_PER_RECORD);

        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        this.relationships = Collections.unmodifiableSet(relationships);

        this.mapper = new ObjectMapper();
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return this.descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        api = new TwitterStreamAPI(context, messageQueue, getLogger());
        final String endpointName = context.getProperty(ENDPOINT).getValue();
        if (ENDPOINT_SAMPLE.getValue().equals(endpointName)) {
            api.start(TwitterStreamAPI.SAMPLE_ENDPOINT);
        }
        else if (ENDPOINT_SEARCH.getValue().equals(endpointName)){
            api.start(TwitterStreamAPI.SEARCH_ENDPOINT);
        }
        else {
            throw new AssertionError("Endpoint was invalid value: " + endpointName);
        }
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        RecordTweetWriter writer = createWriter(context, session);
        processTweets(writer, context, session);
    }

    @OnStopped
    public void onStopped() {
        if (api != null) {
            api.stop();
        }
        api = null;
        emptyQueue();
    }
}