package io.confluent.bootcamp;

import io.confluent.bootcamp.streams.Context;
import io.confluent.bootcamp.streams.SerdeGenerator;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import java.util.Random;
import org.apache.kafka.clients.admin.MockAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class TwitterPart2Test {

  private TopologyTestDriver driver;
  private TestInputTopic<Long, ScoredTweet> inputTopic;
  private TestOutputTopic<Long, GlobalTwitterScore> outputTopic;
  private TestOutputTopic<Void, Void> topicDlq;
  private MockProducer<String, byte[]> dlqProducer;
  private MockAdminClient adminClient;

  @BeforeEach
  void setup() throws ExecutionException, InterruptedException {
    // Building mock clients and schema registry
    dlqProducer = new MockProducer<String, byte[]>();
    adminClient = MockAdminClient.create().numBrokers(1).defaultPartitions((short) 1)
        .defaultReplicationFactor(1).build();
    adminClient.createTopics(
        Collections.singletonList(new NewTopic(Constant.TOPIC_TWEET, 1, (short) 1)));
    Context.setProducer(dlqProducer);
    Context.setAdminClient(adminClient);
    Context.getConfiguration()
        .put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://local");

    // Inserting topology
    TwitterPart2 twitterPart2 = new TwitterPart2();
    StreamsBuilder streamsBuilder = new StreamsBuilder();
    twitterPart2.buildTopology(streamsBuilder);

    // Building topology test driver and topics
    driver = new TopologyTestDriver(streamsBuilder.build());
    inputTopic = driver.createInputTopic(Constant.TOPIC_SCORED_TWEETS, new LongSerializer(),
        SerdeGenerator.<ScoredTweet>getSerde().serializer());
    outputTopic = driver.createOutputTopic(Constant.TOPIC_GLOBAL_SCORE, new LongDeserializer(),
        SerdeGenerator.<GlobalTwitterScore>getSerde().deserializer());
  }

  @AfterEach
  void close() {
    dlqProducer.close();
    driver.close();
  }


  ScoredTweet buildTweet(String text, Long userId, double score) {
    return ScoredTweet.newBuilder()
        .setUserId(userId)
        .setText(text)
        .setCreatedAt(Instant.now())
        .setTweetScore(score)
        .setUserName("Name")
        .setId(12L)
        .build();
  }

  @Test
  void computesGlobalScoreEveryMinute() {
    var range = -2.0 + (2.0 - (-2.0));
    var scoreSum = 0.0;
    ScoredTweet tweet = null;
    var streamTime = Instant.now();
    for (int i = 0; i < 4; i++) {
      tweet = buildTweet("Awesome", 1L, new Random().nextDouble() * range);
      scoreSum += tweet.getTweetScore();
      inputTopic.pipeInput(tweet.getUserId(), tweet, streamTime);
      if (!outputTopic.isEmpty()) {
        var partialScore = outputTopic.readValue();
        assertEquals(scoreSum, partialScore.getTotalAvgScore());
      }
    }

    tweet = buildTweet("Awesome", 1L, new Random().nextDouble() * range);

    inputTopic.pipeInput(tweet.getUserId(), tweet, Instant.now().plus(1, ChronoUnit.MINUTES));
    scoreSum += tweet.getTweetScore();
    var avg = scoreSum / 5;
    var tweetScore = outputTopic.readValue();

    assertEquals(avg, tweetScore.getTotalAvgScore());
  }
}