package io.confluent.bootcamp.transformer;

import io.confluent.bootcamp.ScoredTweet;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public class CreatedAtTimestampExtractor implements TimestampExtractor {

  @Override
  public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
    if (record.value() != null) {
      ScoredTweet tweet =  (ScoredTweet)record.value();
      return  tweet.getCreatedAt().toEpochMilli();
    }
    return record.timestamp();
  }
}
