package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.azure.practices.probe.Probe;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CounterProbe implements Probe {

    private final Logger LOG = LoggerFactory.getLogger(CounterProbe.class);
    private int numberOfProbes = 0;
    private CuratorFramework client;

    public CounterProbe(CuratorFramework client) {
        Preconditions.checkNotNull(client);
        this.client = client;
    }

    @Override
    public void probe() {
        numberOfProbes++;
        LOG.debug("Number of probe executions: " + numberOfProbes);
        if (numberOfProbes > 3) {
            LOG.debug("Threshold reached!");
            numberOfProbes = 0;
        }
    }
}
