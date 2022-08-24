package io.confluent.bootcamp.aggregator;

import io.confluent.bootcamp.Constant;
import io.confluent.bootcamp.ScoredTweet;
import io.confluent.bootcamp.Tweep;
import io.confluent.bootcamp.sentiment.analyzer.TweetSentimentalAnalysis;
import io.confluent.bootcamp.streams.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.streams.kstream.Aggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TweepAggregator implements Aggregator<Long, ScoredTweet, Tweep> {

  static Logger logger = LoggerFactory.getLogger(TweepAggregator.class.getName());

  @Override
  public Tweep apply(Long key, ScoredTweet value, Tweep aggregate) {
    try {
      return doApply(key, value, aggregate);
    }catch (Exception e){
      try {
        Context.sendMessageToDLQ(Constant.TWEEP_DLQ, e, value.toByteBuffer().array());
        return null;
      } catch (ExecutionException | InterruptedException | IOException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private Tweep doApply(Long key, ScoredTweet value, Tweep aggregate) {
    if (aggregate == null){
      aggregate = createEmptyTweep(key);
    }

    List<Long> latestTweetsTime =  aggregate.getLatestTweetsTimes();
    if( latestTweetsTime == null)
        latestTweetsTime = new ArrayList<>();

    latestTweetsTime.add(value.getCreatedAt().toEpochMilli());
    double latestScore = aggregate.getAvgLatestScore();
    aggregate.setAvgLatestScore(computeAvgScore(latestScore, value.getTweetScore()));
    aggregate.setLatestTweetsTimes(latestTweetsTime);

    if (checkIfIgnore(aggregate.getLatestTweetsTimes(), aggregate.getAvgLatestScore())){
      logger.debug("User with ID [{}] state from VALID to IGNORED. Score of the last 5 tweets in the latest 24h is {}",
          key, aggregate.getAvgLatestScore());

      aggregate.setShouldIgnore(true);
      //Reset the list, we don't need to keep aggregating tweets from here
      aggregate.setLatestTweetsTimes(new ArrayList<>());
    }else {
      if (aggregate.getShouldIgnore()) {
        logger.debug("User with ID [{}] state from IGNORED to VALID. Score of the last 5 tweets in the latest 24h is {}",
            key, aggregate.getAvgLatestScore());
      }
      aggregate.setShouldIgnore(false);
    }

    return aggregate;
  }

  private Boolean checkIfIgnore(List<Long> latestTweetsTimes, Double avgScore){
    if (latestTweetsTimes.size() >= Constant.TWEET_NUMBER_LIMIT) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.DAY_OF_MONTH, -1);
      Long yesterdayMillis = cal.getTimeInMillis();

      for (Long ts : latestTweetsTimes) {
        if(ts < yesterdayMillis)
          return false;
      }

      return avgScore <= -1.0 || avgScore >= 1.0;
    }

    return false;
  }

  /**
   * Score is computed based on a qty of 5 elements
   * @param current actual current score before computation. Can be 0
   * @param newScore new value to add up to the avg
   * @return the new avg computed value
   */
  private Double computeAvgScore(Double current, Double newScore) {
    return current + (newScore/Constant.TWEET_NUMBER_LIMIT);
  }

  private Tweep createEmptyTweep(Long userId) {
    return Tweep.newBuilder()
        .setUserId(userId)
        .setLatestTweetsTimes(new ArrayList<>())
        .setTotalTweets(0)
        .setShouldIgnore(false)
        .setAvgLatestScore(0)
        .build();
  }

}
