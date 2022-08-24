package io.confluent.bootcamp;

import com.github.jcustenborder.kafka.connect.twitter.User;
import io.confluent.bootcamp.sentiment.analyzer.TweetSentimentalAnalysis;
import io.confluent.bootcamp.streams.Context;
import io.confluent.bootcamp.streams.SerdeGenerator;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import  com.github.jcustenborder.kafka.connect.twitter.Status;


class TwitterPart1Test {
    private TopologyTestDriver driver;
//    private TestInputTopic<String, Status> topicTweetStatus;
    private TestOutputTopic<Void, Void> topicDlq;
    private MockProducer<String, byte[]> dlqProducer;
    private TestInputTopic<String, Status> inputTweets;
    private TestOutputTopic<Long, ScoredTweet> output;

    @BeforeEach
    void setup() throws ExecutionException, InterruptedException {
        // Building mock client and schema registry
        dlqProducer = new MockProducer<String, byte[]>();
        Context.setProducer(dlqProducer);
        Context.getConfiguration().put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://local");
        Context.getConfiguration().put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
            String.valueOf(Serdes.Long().getClass()));
        Context.setAnalyzer(new TweetSentimentalAnalysis());

        // Inserting topology
        TwitterPart1 twitterPart1 = new TwitterPart1();
        StreamsBuilder streamsBuilder = new StreamsBuilder();
        twitterPart1.buildTopology(streamsBuilder);

        // Building topology test driver and topics
        driver = new TopologyTestDriver(streamsBuilder.build());
        inputTweets = driver.createInputTopic(Constant.TOPIC_TWEET, new StringSerializer(), SerdeGenerator.<Status>getSerde().serializer());
        output = driver.createOutputTopic(Constant.TOPIC_SCORED_TWEETS, new LongDeserializer(), SerdeGenerator.<ScoredTweet>getSerde().deserializer());
    }

    @AfterEach
    void close() {
        dlqProducer.close();
        driver.close();
    }

    Status buildTweet(String text, Long userId, Instant creationTime) {
        Status status = new Status();
        status.setText(text);
        status.setId(new Random().nextLong());
        status.setUser(User.newBuilder()
                .setId(userId)
                .setName("test user")
                .setWithheldInCountries(Collections.emptyList())
                .setFollowersCount(100)
                .setFavouritesCount(21)
                .build());

        status.setCreatedAt(creationTime);
        status.setWithheldInCountries(Collections.singletonList("World"));
        status.setContributors(Collections.emptyList());
        return status;
    }

    @Test
    void simpleCase() {
        Status tweet = buildTweet("BTC is shit", 1L, Instant.now());
        inputTweets.pipeInput(tweet);

        ScoredTweet scored = output.readValue();
        assertTrue(scored.getTweetScore() >= -1.0 || scored.getTweetScore() <= 1.0);
    }

    @Test
    void shouldBeIgnoredByScoreAfter5Tweets(){
        ScoredTweet record;
        int count = 1;
        for (int i = 0 ; i < 4 ; i++){
            Status tweet = buildTweet("Awesome!", 1L, Instant.now());
            inputTweets.pipeInput(tweet, Instant.now());
            count++;
            record = output.readValue();
            assertNotNull(record.getTweetScore());
        }

        Status tweet = buildTweet("Awesome", 1L, Instant.now());
        inputTweets.pipeInput(tweet, Instant.now());
        assertTrue(output.isEmpty());
    }

    @Test
    void shouldNotBeIgnoredByScoreAfter24H(){
        ScoredTweet record;
        int count = 1;
        for (int i = 0 ; i < 4 ; i++){
            Status tweet = buildTweet("Awesome!", 1L, Instant.now());
            inputTweets.pipeInput(tweet, Instant.now());
            count++;
            record = output.readValue();
            assertNotNull(record.getTweetScore());
        }

        Status tweet = buildTweet("Awesome", 1L, Instant.now());
        inputTweets.pipeInput(tweet, Instant.now());
        assertTrue(output.isEmpty());

        inputTweets.pipeInput(tweet, Instant.now().plus(24, ChronoUnit.HOURS));
        record = output.readValue();
        assertNotNull(record.getTweetScore());

    }

}