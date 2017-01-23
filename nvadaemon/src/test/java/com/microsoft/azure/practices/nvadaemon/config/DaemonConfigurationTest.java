package com.microsoft.azure.practices.nvadaemon.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class DaemonConfigurationTest {
    @Test
    void test_null_monitors() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new DaemonConfiguration(null, null));
    }

    @Test
    void test_empty_monitors() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new DaemonConfiguration(monitors, null));
    }

    @Test
    void test_valid_monitors() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(new MonitorConfiguration("com.company.Monitor", null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        Assertions.assertEquals(monitors, daemonConfiguration.getMonitors());
    }

    @Test
    void test_default_shutdown_await_time() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(new MonitorConfiguration("com.company.Monitor", null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        Assertions.assertEquals(DaemonConfiguration.DEFAULT_SHUTDOWN_AWAIT_TIME,
            daemonConfiguration.getShutdownAwaitTime());
    }

    @Test
    void test_zero_shutdown_await_time() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(new MonitorConfiguration("com.company.Monitor", null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, 0);
        Assertions.assertEquals(DaemonConfiguration.DEFAULT_SHUTDOWN_AWAIT_TIME,
            daemonConfiguration.getShutdownAwaitTime());
    }

    @Test
    void test_negative_shutdown_await_time() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(new MonitorConfiguration("com.company.Monitor", null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, -1);
        Assertions.assertEquals(DaemonConfiguration.DEFAULT_SHUTDOWN_AWAIT_TIME,
            daemonConfiguration.getShutdownAwaitTime());
    }

    @Test
    void test_valid_shutdown_await_time() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(new MonitorConfiguration("com.company.Monitor", null));
        Integer shutdownAwaitTime = 1000;
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors,
            shutdownAwaitTime);
        Assertions.assertEquals(shutdownAwaitTime.intValue(),
            daemonConfiguration.getShutdownAwaitTime());
    }
}
