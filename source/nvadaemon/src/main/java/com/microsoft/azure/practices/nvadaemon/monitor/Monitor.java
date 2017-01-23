package com.microsoft.azure.practices.nvadaemon.monitor;

import com.microsoft.azure.practices.nvadaemon.config.MonitorConfiguration;

public interface Monitor extends AutoCloseable {
    //void init(MonitorConfiguration configuration) throws Exception;
    void init() throws Exception;
}
