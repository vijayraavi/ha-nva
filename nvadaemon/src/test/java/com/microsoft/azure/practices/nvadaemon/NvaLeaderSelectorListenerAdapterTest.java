package com.microsoft.azure.practices.nvadaemon;

import com.microsoft.azure.practices.nvadaemon.config.DaemonConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.MonitorConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.NvaDaemonConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.ZookeeperConfiguration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.ACLBackgroundPathAndBytesable;
import org.apache.curator.framework.api.ACLCreateModeBackgroundPathAndBytesable;
import org.apache.curator.framework.api.CreateBuilder;
import org.apache.curator.framework.api.ProtectACLCreateModePathAndBytesable;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.listen.Listenable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NvaLeaderSelectorListenerAdapterTest {
    @Test
    void testNullNvaDaemonConfiguration() {
        CuratorFramework curatorFramework = mock(CuratorFramework.class);
        Assertions.assertThrows(NullPointerException.class,
            () -> new NvaLeaderSelectorListenerAdapter(null, curatorFramework));
    }

    @Test
    void testNullCuratorFramework() {
        NvaDaemonConfiguration nvaDaemonConfiguration = mock(NvaDaemonConfiguration.class);
        Assertions.assertThrows(NullPointerException.class,
            () -> new NvaLeaderSelectorListenerAdapter(nvaDaemonConfiguration, null));
    }

    @Test
    void testStartCloseAdapter() throws Exception {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, null);
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("probeReturnValue", "false");
        settings.put("awaitTime", "2000");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$ScheduledMonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaDaemonConfiguration nvaDaemonConfiguration = new NvaDaemonConfiguration(
            zookeeperConfiguration, daemonConfiguration);

        CuratorFramework curatorFramework = mock(CuratorFramework.class);
        when(curatorFramework.getConnectionStateListenable())
            .thenReturn(mock(Listenable.class));
        NvaLeaderSelectorListenerAdapter nvaLeaderSelectorListenerAdapter =
            new NvaLeaderSelectorListenerAdapter(nvaDaemonConfiguration, curatorFramework);
        nvaLeaderSelectorListenerAdapter.start();
        nvaLeaderSelectorListenerAdapter.close();
    }

    @Test
    void testTakeLeadership() throws Exception {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, null);
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("probeReturnValue", "false");
        settings.put("awaitTime", "2000");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$ScheduledMonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaDaemonConfiguration nvaDaemonConfiguration = new NvaDaemonConfiguration(
            zookeeperConfiguration, daemonConfiguration);

        CuratorFramework curatorFramework = mock(CuratorFramework.class);
        NvaLeaderSelectorListenerAdapter nvaLeaderSelectorListenerAdapter =
            new NvaLeaderSelectorListenerAdapter(nvaDaemonConfiguration, curatorFramework);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Void> task = executorService.submit(
            () -> {
                nvaLeaderSelectorListenerAdapter.takeLeadership(curatorFramework);
                return null;
            });
        Thread.sleep(3000);
        Assertions.assertThrows(IllegalStateException.class,
            () -> nvaLeaderSelectorListenerAdapter.close());
        task.get();
    }

    @Test
    void testTakeLeadershipInterrupted() throws Exception {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, null);
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("constructorShouldThrowException", "false");
        settings.put("shouldCauseInterrupt", "false");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$MonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaDaemonConfiguration nvaDaemonConfiguration = new NvaDaemonConfiguration(
            zookeeperConfiguration, daemonConfiguration);

        CuratorFramework curatorFramework = mock(CuratorFramework.class);
        NvaLeaderSelectorListenerAdapter nvaLeaderSelectorListenerAdapter =
            new NvaLeaderSelectorListenerAdapter(nvaDaemonConfiguration, curatorFramework);
        AtomicReference<Thread> thread = new AtomicReference<>();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Void> task = executorService.submit(
            () -> {
                thread.set(Thread.currentThread());
                nvaLeaderSelectorListenerAdapter.takeLeadership(curatorFramework);
                return null;
            });
        Thread.sleep(3000);
        thread.get().interrupt();
//        Assertions.assertThrows(IllegalStateException.class,
//            () -> nvaLeaderSelectorListenerAdapter.close());
        task.get();
//        Assertions.assertThrows(ExecutionException.class,
//            () -> nvaLeaderSelectorListenerAdapter.takeLeadership(curatorFramework));
    }

    @Test
    void testTakeLeadershipExecutionException() throws Exception {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, null);
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put(NvaMonitorTest.MonitorValidConstructor.INIT_SHOULD_THROW_EXCEPTION, "true");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$MonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaDaemonConfiguration nvaDaemonConfiguration = new NvaDaemonConfiguration(
            zookeeperConfiguration, daemonConfiguration);

        CuratorFramework curatorFramework = mock(CuratorFramework.class);
        NvaLeaderSelectorListenerAdapter nvaLeaderSelectorListenerAdapter =
            new NvaLeaderSelectorListenerAdapter(nvaDaemonConfiguration, curatorFramework);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Void> task = executorService.submit(
            () -> {
                nvaLeaderSelectorListenerAdapter.takeLeadership(curatorFramework);
                return null;
            });
        Assertions.assertThrows(ExecutionException.class,
            () -> task.get());
    }

    @Test
    void testTakeLeadershipNvaMonitorException() throws Exception {
        ZookeeperConfiguration zookeeperConfiguration =
            new ZookeeperConfiguration("connection-string", "/leader-selector-path", null, null);
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$InvalidMonitor",
                null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaDaemonConfiguration nvaDaemonConfiguration = new NvaDaemonConfiguration(
            zookeeperConfiguration, daemonConfiguration);

        CuratorFramework curatorFramework = mock(CuratorFramework.class);
        NvaLeaderSelectorListenerAdapter nvaLeaderSelectorListenerAdapter =
            new NvaLeaderSelectorListenerAdapter(nvaDaemonConfiguration, curatorFramework);
        Assertions.assertThrows(NvaMonitor.NvaMonitorException.class,
            () -> nvaLeaderSelectorListenerAdapter.takeLeadership(curatorFramework));

    }
}
