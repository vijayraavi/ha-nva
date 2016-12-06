package com.microsoft.azure.practices.nvadaemon.config;

import java.util.HashMap;
import java.util.Map;

public class MonitorConfiguration {
    private String monitorClass;

    private Map<String, Object> settings = new HashMap<>();

    public MonitorConfiguration() {
    }

    public String getMonitorClass() { return this.monitorClass; }

    public Map<String, Object> getSettings() { return this.settings; }
}
