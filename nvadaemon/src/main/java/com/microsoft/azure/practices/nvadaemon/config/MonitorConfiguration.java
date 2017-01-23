package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.HashMap;
import java.util.Map;

public class MonitorConfiguration {
    private String monitorClass;
    private Map<String, Object> settings = new HashMap<>();

    @JsonCreator
    public MonitorConfiguration(@JsonProperty("monitorClass")String monitorClass,
                                @JsonProperty("settings")Map<String, Object> settings) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(monitorClass),
            "monitorClass cannot be null or empty");
        this.monitorClass = monitorClass;
        if (settings != null) {
            this.settings = settings;
        }
    }

    public String getMonitorClass() { return this.monitorClass; }

    public Map<String, Object> getSettings() { return this.settings; }
}
