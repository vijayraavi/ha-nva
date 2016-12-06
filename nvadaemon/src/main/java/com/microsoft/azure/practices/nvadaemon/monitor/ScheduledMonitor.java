package com.microsoft.azure.practices.nvadaemon.monitor;

import java.util.concurrent.TimeUnit;

public interface ScheduledMonitor extends Monitor {
    boolean probe();
    void execute();
    int getTime();
    TimeUnit getUnit();
}