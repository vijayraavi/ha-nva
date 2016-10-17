package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class ZookeeperConfiguration {
    private static final int DEFAULT_RETRY_SLEEP_TIME_MS = 3000;
    private static final int DEFAULT_NUMBER_OF_RETRIES = 5;

    private String connectionString;
    private String leaderSelectorPath;

    private int retrySleepTimeMs = DEFAULT_RETRY_SLEEP_TIME_MS;
    private int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;

    @JsonCreator
    public ZookeeperConfiguration() {
    }

    public String getConnectionString() { return this.connectionString; }

    public int getRetrySleepTimeMs() { return this.retrySleepTimeMs; }

    public int getNumberOfRetries() { return this.numberOfRetries; }

    public String getLeaderSelectorPath() { return this.leaderSelectorPath; }

    public void validate() throws ConfigurationException {
        try {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(this.connectionString),
                "connectionString cannot be null or empty");
            Preconditions.checkArgument(!Strings.isNullOrEmpty(this.leaderSelectorPath),
                "leaderSelectorPath cannot be null or empty");
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("ZookeeperConfiguration error", e);
        }
    }
}
