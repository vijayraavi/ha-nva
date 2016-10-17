package com.microsoft.azure.practices.nvadaemon.config;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

public class DaemonConfiguration {
    private static final int DEFAULT_SHUTDOWN_AWAIT_TIME_MS = 5000;

    private int shutdownAwaitTimeMs = DEFAULT_SHUTDOWN_AWAIT_TIME_MS;
    private List<MonitorConfiguration> monitors = new ArrayList<>();

    public DaemonConfiguration() {
    }

    public int getShutdownAwaitTimeMs() { return this.shutdownAwaitTimeMs; }

    public List<MonitorConfiguration> getMonitors() { return this.monitors; }

    public void validate() throws ConfigurationException {
        try {
            Preconditions.checkNotNull(this.monitors, "monitors cannot be null");
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("DaemonConfiguration error", e);
        }

        if (this.monitors.size() == 0) {
            throw new ConfigurationException("No monitors found in configuration");
        }
    }
}
