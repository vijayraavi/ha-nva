package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class DaemonConfiguration {
    public static final int DEFAULT_SHUTDOWN_AWAIT_TIME = 5000;

    private int shutdownAwaitTime = DEFAULT_SHUTDOWN_AWAIT_TIME;
    private List<MonitorConfiguration> monitors = new ArrayList<>();

    @JsonCreator
    public DaemonConfiguration(@JsonProperty("monitors")List<MonitorConfiguration> monitors,
                               @JsonProperty("shutdownAwaitTime")Integer shutdownAwaitTime) {
        if ((monitors == null) || (monitors.size() == 0)) {
            throw new IllegalArgumentException("No monitors found in configuration");
        }

        this.monitors = monitors;

        if ((shutdownAwaitTime != null) && (shutdownAwaitTime > 0)) {
            this.shutdownAwaitTime = shutdownAwaitTime;
        }
    }

    public int getShutdownAwaitTime() { return this.shutdownAwaitTime; }

    public List<MonitorConfiguration> getMonitors() { return this.monitors; }
}
