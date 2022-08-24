package io.confluent.bootcamp;

public class Constant {

    public static final String STORE_DUMMY ="dummy";
    public static final String SCORE_STORE = "goblal-store-score";
    public static final String TOPIC_TWEET = "twitter-tweets";
    public static final String STORE_TWEEPS = "victor-tweep-score-store";
    public static final String REPARTITION_NUSER = "victor-repartition-by-tweep";
    public static final Integer TWEET_NUMBER_LIMIT = 5;
    public static final String TWEET_TWEEP_JOIN = "victor-tweet-tweep-join";
    public static final String TOPIC_SCORED_TWEETS = "victor-scored-tweets";
    public static final String TOPIC_GLOBAL_SCORE = "victor-global-twitter-score";

    public static final String TWEEP_DLQ = "victor-tweep-dlq";
    public static final String SCORING_DLQ = "victor-scoring-dlq";
}
