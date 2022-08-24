package io.confluent.bootcamp.mapper;

import com.github.jcustenborder.kafka.connect.twitter.Status;
import io.confluent.bootcamp.ScoredTweet;
import io.confluent.bootcamp.sentiment.analyzer.TweetSentimentalAnalysis;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScoredTweetKeyValueMapper implements KeyValueMapper<String, Status, KeyValue<Long, ScoredTweet>> {

  static Logger logger = LoggerFactory.getLogger(ScoredTweetKeyValueMapper.class.getName());
  /**
   * Maps the huge Status object from Twitter to a more minimal tweet object with just the data
   * needed. Changes the Key to the user ID for later aggregation
   * @param key   the key of the original Object
   * @param value the value of original object
   * @return
   */
  @Override
  public KeyValue<Long, ScoredTweet> apply(String key, Status value) {

    ScoredTweet scoredTweet = new ScoredTweet();
    scoredTweet.setCreatedAt(value.getCreatedAt());
    scoredTweet.setId(value.getId());
    scoredTweet.setText(value.getText());
    scoredTweet.setUserId(value.getUser().getId());
    scoredTweet.setUserName(value.getUser().getName());
    scoredTweet.setUserFollowersCount(value.getUser().getFollowersCount());

    return KeyValue.pair(scoredTweet.getUserId(), scoredTweet);
  }

}
