package com.microsoft.azure.practices.nvadaemon.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ZookeeperConfigurationTest {
    @Test
    void test_null_connection_string() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ZookeeperConfiguration(null, null, null, null));
    }

    @Test
    void test_empty_connection_string() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ZookeeperConfiguration("", null, null, null));
    }

    @Test
    void test_null_leader_selector_path() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ZookeeperConfiguration("connection_string", null, null, null));
    }

    @Test
    void test_empty_leader_selector_path() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ZookeeperConfiguration("connection_string", "", null, null));
    }

    @Test
    void test_invalid_leader_selector_path() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ZookeeperConfiguration("connection-string", "invalid-path", null, null));
    }

    @Test
    void test_default_retry_sleep_time() {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, null);
        Assertions.assertEquals(ZookeeperConfiguration.DEFAULT_RETRY_SLEEP_TIME,
            zookeeperConfiguration.getRetrySleepTime());
    }

    @Test
    void test_zero_retry_sleep_time() {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", 0, null);
        Assertions.assertEquals(ZookeeperConfiguration.DEFAULT_RETRY_SLEEP_TIME,
            zookeeperConfiguration.getRetrySleepTime());
    }

    @Test
    void test_negative_retry_sleep_time() {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", -1, null);
        Assertions.assertEquals(ZookeeperConfiguration.DEFAULT_RETRY_SLEEP_TIME,
            zookeeperConfiguration.getRetrySleepTime());
    }

    @Test
    void test_valid_retry_sleep_time() {
        Integer retrySleepTime = 5000;
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path",
                retrySleepTime, null);
        Assertions.assertEquals(retrySleepTime.intValue(),
            zookeeperConfiguration.getRetrySleepTime());
    }

    @Test
    void test_default_number_of_retries() {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, null);
        Assertions.assertEquals(ZookeeperConfiguration.DEFAULT_NUMBER_OF_RETRIES,
            zookeeperConfiguration.getNumberOfRetries());
    }

    @Test
    void test_zero_number_of_retries() {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, 0);
        Assertions.assertEquals(ZookeeperConfiguration.DEFAULT_NUMBER_OF_RETRIES,
            zookeeperConfiguration.getNumberOfRetries());
    }

    @Test
    void test_negative_number_of_retries() {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, -1);
        Assertions.assertEquals(ZookeeperConfiguration.DEFAULT_NUMBER_OF_RETRIES,
            zookeeperConfiguration.getNumberOfRetries());
    }

    @Test
    void test_valid_number_of_retries() {
        Integer numberOfRetries = 3;
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path",
                null, numberOfRetries);
        Assertions.assertEquals(numberOfRetries.intValue(),
            zookeeperConfiguration.getNumberOfRetries());
    }

    @Test
    void test_valid_parameters() {
        String connectionString = "connection-string";
        String leaderSelectorPath = "/leader-selector-path";
        Integer numberOfRetries = 5;
        Integer retrySleepTime = 5000;
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration(connectionString, leaderSelectorPath,
                retrySleepTime, numberOfRetries);
        Assertions.assertEquals(connectionString,
            zookeeperConfiguration.getConnectionString());
        Assertions.assertEquals(leaderSelectorPath,
            zookeeperConfiguration.getLeaderSelectorPath());
        Assertions.assertEquals(numberOfRetries.intValue(),
            zookeeperConfiguration.getNumberOfRetries());
        Assertions.assertEquals(retrySleepTime.intValue(),
            zookeeperConfiguration.getRetrySleepTime());
    }
}
