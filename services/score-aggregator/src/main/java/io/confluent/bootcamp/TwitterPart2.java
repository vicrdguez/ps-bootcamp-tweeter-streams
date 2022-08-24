package io.confluent.bootcamp;

import io.confluent.bootcamp.rest.RestServerWithStaticResource;
import io.confluent.bootcamp.streams.SerdeGenerator;
import io.confluent.bootcamp.streams.StreamApp;
import io.confluent.bootcamp.transformer.CreatedAtTimestampExtractor;
import io.confluent.bootcamp.transformer.ScoringTransformer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutionException;


public class TwitterPart2 extends StreamApp {
    static Logger logger = LoggerFactory.getLogger(TwitterPart2.class.getName());

    public static void main(String[] args) throws Exception {
        Properties extraProperties = new Properties();
        TwitterPart2 streamApp = new TwitterPart2();
        extraProperties.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG,
            CreatedAtTimestampExtractor.class.getName());
        extraProperties.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/tweet-scoring");
        extraProperties.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "victor-scoring-app");
        streamApp.run(args, extraProperties);
        RestServerWithStaticResource restServerWithStaticResource = new RestServerWithStaticResource();
        restServerWithStaticResource.run();
    }

    @Override
    protected void buildTopology(StreamsBuilder builder) throws ExecutionException, InterruptedException {
        builder.addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(Constant.SCORE_STORE),
            Serdes.Long(),
            SerdeGenerator.getSerde()
        ));

        KStream<Long, ScoredTweet> scoredStream = builder.stream(Constant.TOPIC_SCORED_TWEETS,
            Consumed.with(Serdes.Long(), SerdeGenerator.<ScoredTweet>getSerde())
                .withName("victor-scoring-consumer"));

        scoredStream
            .transform(ScoringTransformer::new, Named.as("victor-score-transformer"), Constant.SCORE_STORE)
            .peek((k,v) -> logger.info("Sending global score for user [{}] => [{}]", k, v))
            .to(Constant.TOPIC_GLOBAL_SCORE,
                Produced.<Long, GlobalTwitterScore>as("victor-global-score")
                .withKeySerde(Serdes.Long()).withValueSerde(SerdeGenerator.getSerde()));


    }
}
