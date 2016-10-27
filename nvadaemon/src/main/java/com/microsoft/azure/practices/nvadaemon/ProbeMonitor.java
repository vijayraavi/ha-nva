package com.microsoft.azure.practices.nvadaemon;

import com.microsoft.azure.practices.monitor.ScheduledMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProbeMonitor implements ScheduledMonitor {

    private final Logger log = LoggerFactory.getLogger(ProbeMonitor.class);
    private int failures;

    @Override
    public void init(Map<String, String> config) {
        failures = 0;
    }

    @Override
    public boolean probe() {
        return ++failures < 5;
    }

    @Override
    public void execute() {
        log.info("Probe failure.  Executing failure action.");
        failures = 0;
        // Simulate long running action
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            log.error("Long running execute interrupted");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int getTime() {
        return 3000;
    }

    @Override
    public TimeUnit getUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    public void close() throws IOException {

    }
}
