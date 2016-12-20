package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class ZookeeperConfiguration {
    private static final int DEFAULT_RETRY_SLEEP_TIME = 3000;
    private static final int DEFAULT_NUMBER_OF_RETRIES = 5;

    private String connectionString;
    private String leaderSelectorPath;

    private int retrySleepTime = DEFAULT_RETRY_SLEEP_TIME;
    private int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;

    @JsonCreator
    public ZookeeperConfiguration(@JsonProperty("connectionString")String connectionString,
                                  @JsonProperty("leaderSelectorPath")String leaderSelectorPath,
                                  @JsonProperty("retrySleepTime")Integer retrySleepTime,
                                  @JsonProperty("numberOfRetries")Integer numberOfRetries) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(connectionString),
            "connectionString cannot be null or empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(leaderSelectorPath),
            "leaderSelectorPath cannot be null or empty");
        this.connectionString = connectionString;
        this.leaderSelectorPath = leaderSelectorPath;
        if (retrySleepTime != null) {
            this.retrySleepTime = retrySleepTime;
        }

        if (numberOfRetries != null) {
            this.numberOfRetries = numberOfRetries;
        }
    }

    public String getConnectionString() { return this.connectionString; }

    public int getRetrySleepTime() { return this.retrySleepTime; }

    public int getNumberOfRetries() { return this.numberOfRetries; }

    public String getLeaderSelectorPath() { return this.leaderSelectorPath; }
}
