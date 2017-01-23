package com.microsoft.azure.practices.nvadaemon.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class MonitorConfigurationTest {
    @Test
    void test_null_monitor_class() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new MonitorConfiguration(null, null));
    }

    @Test
    void test_empty_monitor_class() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new MonitorConfiguration("", null));
    }

    @Test
    void test_valid_monitor_class() {
        String monitorClass = "com.company.Monitor";
        MonitorConfiguration monitorConfiguration =
            new MonitorConfiguration(monitorClass, null);
        Assertions.assertEquals(monitorClass, monitorConfiguration.getMonitorClass());
    }

    @Test
    void test_null_settings() {
        MonitorConfiguration monitorConfiguration =
            new MonitorConfiguration("com.company.Monitor", null);
        Assertions.assertNotNull(monitorConfiguration.getSettings());
    }

    @Test
    void test_valid_settings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("setting", "value");
        MonitorConfiguration monitorConfiguration =
            new MonitorConfiguration("com.company.Monitor", settings);
        Assertions.assertEquals("value", monitorConfiguration.getSettings().get("setting"));
    }
}
