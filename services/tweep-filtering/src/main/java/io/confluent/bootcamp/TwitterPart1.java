package io.confluent.bootcamp;

import com.github.jcustenborder.kafka.connect.twitter.Status;
import io.confluent.bootcamp.aggregator.TweepAggregator;
import io.confluent.bootcamp.mapper.ScoredTweetKeyValueMapper;
import io.confluent.bootcamp.rest.RestServer;
import io.confluent.bootcamp.sentiment.analyzer.TweetSentimentalAnalysis;
import io.confluent.bootcamp.streams.Context;
import io.confluent.bootcamp.streams.SerdeGenerator;
import io.confluent.bootcamp.streams.StreamApp;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Properties;


public class TwitterPart1 extends StreamApp {

  static Logger logger = LoggerFactory.getLogger(TwitterPart1.class.getName());

  public static void main(String[] args) throws Exception {
    TwitterPart1 streamApp = new TwitterPart1();
    Properties extraProperties = new Properties();
    extraProperties.setProperty(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0");
    extraProperties.setProperty(StreamsConfig.STATE_DIR_CONFIG, "/tmp/tweet-filter");
    extraProperties.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "victor-filter-app");
    Context.setAnalyzer(new TweetSentimentalAnalysis());
    streamApp.run(args, extraProperties);
    RestServer restServer = new RestServer();
    restServer.run();
  }

  @Override
  protected void buildTopology(StreamsBuilder builder) {
    KStream<String, Status> inputTweetStream = builder.stream(Constant.TOPIC_TWEET,
        Consumed.with(Serdes.String(), SerdeGenerator.<Status>getSerde())
            .withName("victor-status-consumer"));

    KStream<Long, ScoredTweet> scoredTweetStream = inputTweetStream
        //Maps to a lightweight object and changes the key to the user Id. Then explicit repartition
        .map((k,v) -> {

          ScoredTweet scoredTweet = new ScoredTweet();
          scoredTweet.setCreatedAt(v.getCreatedAt());
          scoredTweet.setId(v.getId());
          scoredTweet.setText(v.getText());
          scoredTweet.setUserId(v.getUser().getId());
          scoredTweet.setUserName(v.getUser().getName());
          scoredTweet.setUserFollowersCount(v.getUser().getFollowersCount());

          double score = Context.getAnalyzer().score(String.valueOf(v.getText()));
          scoredTweet.setTweetScore(score);

          return KeyValue.pair(scoredTweet.getUserId(), scoredTweet);
        }, Named.as("victor-status-to-tweet"))
        .repartition(Repartitioned.<Long, ScoredTweet>as(Constant.REPARTITION_NUSER).withKeySerde(
            Serdes.Long()).withValueSerde(SerdeGenerator.getSerde()))
        .filter((k,v) -> v.getTweetScore() != null);


    KTable<Long, Tweep> tweeptable = scoredTweetStream
        //Grouping by userID so we can aggregate score within 24h
        .groupByKey()
        .aggregate(Tweep::new,
            new TweepAggregator(),
            Materialized.<Long, Tweep, KeyValueStore<Bytes, byte[]>>as(Constant.STORE_TWEEPS)
                .withKeySerde(Serdes.Long())
                .withValueSerde(SerdeGenerator.<Tweep>getSerde()))
        .filter((k, v) -> !v.getShouldIgnore());

    //Dummy join just to get downstream all tweets with non-ignored tweeps
    scoredTweetStream
        .join(tweeptable,
            (tweet, tweep) -> tweet,
            Joined.<Long, ScoredTweet, Tweep>as(Constant.TWEET_TWEEP_JOIN)
                .withKeySerde(Serdes.Long())
                .withValueSerde(SerdeGenerator.<ScoredTweet>getSerde()))
        .peek((k, v) -> logger.info("Writing valid Scored tweet from user [{}] with score [{}]", k, v.getTweetScore()))
        .to(Constant.TOPIC_SCORED_TWEETS, Produced.<Long, ScoredTweet>as("victor-scored-tweets-producer")
            .withKeySerde(Serdes.Long())
            .withValueSerde(SerdeGenerator.getSerde()));
  }


}
