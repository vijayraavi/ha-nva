package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.azure.practices.nvadaemon.NvaMonitor.NvaMonitorException;
import com.microsoft.azure.practices.nvadaemon.config.NvaDaemonConfiguration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NvaLeaderSelectorListenerAdapter
    extends LeaderSelectorListenerAdapter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NvaLeaderSelectorListenerAdapter.class);
    private final LeaderSelector leaderSelector;
    private final NvaDaemonConfiguration configuration;
    private final NvaMonitor nvaMonitor;

    public NvaLeaderSelectorListenerAdapter(NvaDaemonConfiguration configuration,
                                            CuratorFramework client) {
        super();
        this.configuration = Preconditions.checkNotNull(configuration,
            "configuration cannot be null");
        Preconditions.checkNotNull(client, "client cannot be null");
        leaderSelector = new LeaderSelector(client,
            this.configuration.getZookeeperConfiguration().getLeaderSelectorPath(), this);
        leaderSelector.autoRequeue();
        this.nvaMonitor = new NvaMonitor(this.configuration.getDaemonConfiguration());
    }

    public synchronized void start() {
        log.debug("NvaLeaderSelectorListenerAdapter::start()");
        leaderSelector.start();
        log.debug("NvaLeaderSelectorListenerAdapter::start() complete");
    }

    public synchronized void close() throws Exception {
        log.debug("NvaLeaderSelectorListenerAdapter::close()");
        try {
            nvaMonitor.close();
        } catch (Exception e) {
            log.error("Error closing nvaMonitor", e);
            throw e;
        } finally {
            leaderSelector.close();
            log.debug("NvaLeaderSelectorListenerAdapter::close() complete");
        }
    }

    @Override
    public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
        log.info("Leadership acquired");
        try {
            log.info("Starting nvaMonitor");
            Future<Void> task = nvaMonitor.start();
            task.get();
            log.debug("task.get() returned");
        } catch (InterruptedException e) {
            log.info("takeLeadership interrupted");
            Thread.currentThread().interrupt();
        } catch (NvaMonitorException e){
            log.error("Error creating NvaMonitor", e);
            throw e;
        } catch (ExecutionException e) {
            // This should happen when there is an exception starting the nvaMonitor.  We need to
            // relinquish leadership so someone else can try.  However, if this is a configuration
            // issue, it will just bounce from leader to leader, so we need to log it.
            log.error("Error executing NvaMonitor", e);
            throw e;
        } finally {
            this.nvaMonitor.stop();
        }
    }
}
