package io.confluent.bootcamp.transformer;

import io.confluent.bootcamp.Constant;
import io.confluent.bootcamp.GlobalTwitterScore;
import io.confluent.bootcamp.ScoredTweet;
import io.confluent.bootcamp.streams.Context;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

public class ScoringTransformer implements
    Transformer<Long, ScoredTweet, KeyValue<Long, GlobalTwitterScore>> {

  private KeyValueStore<Long, GlobalTwitterScore> scoreStore;
  private ProcessorContext localContext;

  @Override
  public void init(ProcessorContext context) {
    scoreStore = context.getStateStore(Constant.SCORE_STORE);
    localContext = context;
    localContext.schedule(Duration.ofMinutes(1),
        PunctuationType.WALL_CLOCK_TIME,
        (timestamp) -> punctuate());
  }


  @Override
  public KeyValue<Long, GlobalTwitterScore> transform(Long key, ScoredTweet value) {
    GlobalTwitterScore currentScore = scoreStore.get(key);

    if (currentScore == null) {
      currentScore = createEmptyGlobalScore();
    }

    currentScore.setTotalScore(currentScore.getTotalScore() + value.getTweetScore());
    currentScore.setTotalTweets(currentScore.getTotalTweets() + 1);
    currentScore.setTimeOfScore(Instant.now());
    scoreStore.put(key, currentScore);

    return null;
  }

  private GlobalTwitterScore createEmptyGlobalScore() {
    return GlobalTwitterScore.newBuilder()
        .setTotalScore(0.0)
        .setTimeOfScore(Instant.now())
        .setTotalTweets(0)
        .setTotalAvgScore(0.0)
        .build();
  }

  private void punctuate() {
    var recordIterator = scoreStore.all();
    try {
      while (recordIterator.hasNext()) {
        var record = recordIterator.next();
        GlobalTwitterScore twitterScore = record.value;
        double avgScore = twitterScore.getTotalScore() / twitterScore.getTotalTweets();
        twitterScore.setTotalAvgScore(avgScore);

        localContext.forward(record.key, twitterScore);
      }
    } catch (Exception e) {
      try {
        Context.sendMessageToDLQ(Constant.SCORING_DLQ, e,
            recordIterator.next().value.toByteBuffer().array());
      } catch (ExecutionException | InterruptedException | IOException ex) {
        throw new RuntimeException(ex);
      }
    } finally {
      recordIterator.close();
    }
  }

  @Override
  public void close() {

  }
}
