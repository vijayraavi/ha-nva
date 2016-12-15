package com.microsoft.azure.practices.nvadaemon.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.Preconditions;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class NvaDaemonConfigurationTest {
    @Test
    void test_null_zookeeper_configuration() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new NvaDaemonConfiguration(null, null));
    }

    @Test
    void test_null_daemon_configuration() {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, null);
        Assertions.assertThrows(NullPointerException.class,
            () -> new NvaDaemonConfiguration(zookeeperConfiguration, null));
    }

    @Test
    void test_null_reader() {
        Assertions.assertThrows(NullPointerException.class,
            () -> NvaDaemonConfiguration.parseConfig(null));
    }

    @Test
    void test_invalid_reader() {
        try (StringReader reader = new StringReader("")) {
            Assertions.assertThrows(ConfigurationException.class,
                () -> NvaDaemonConfiguration.parseConfig(reader));
        }
    }
    @Test
    void test_valid_parameters() {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, null);
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(new MonitorConfiguration("com.company.Monitor", null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaDaemonConfiguration nvaDaemonConfiguration = new NvaDaemonConfiguration(
            zookeeperConfiguration, daemonConfiguration);
        Assertions.assertEquals(zookeeperConfiguration,
            nvaDaemonConfiguration.getZookeeperConfiguration());
        Assertions.assertEquals(daemonConfiguration,
            nvaDaemonConfiguration.getDaemonConfiguration());
    }
}
