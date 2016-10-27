package com.microsoft.azure.practices.monitor;

import java.util.concurrent.TimeUnit;

public interface ScheduledMonitor extends Monitor {
    boolean probe();
    void execute();
    int getTime();
    TimeUnit getUnit();
}