package com.microsoft.azure.practices.nvadaemon;

import com.microsoft.azure.practices.nvadaemon.config.DaemonConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.MonitorConfiguration;
import com.microsoft.azure.practices.nvadaemon.monitor.Monitor;
import com.microsoft.azure.practices.nvadaemon.monitor.ScheduledMonitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SystemPropertyCondition.SystemProperty(key = "testMonitors", value = "enabled")
public class NvaMonitorTest {
    @Test
    void testNullDaemonConfiguration() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new NvaMonitor(null));
    }

    @Test
    void testMonitorDoesNotImplementRequiredInterfaces() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$MonitorWithoutValidInterfaces",
                null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        Assertions.assertThrows(NvaMonitor.NvaMonitorException.class,
            () -> nvaMonitor.start());
    }

    @Test
    void testScheduledMonitorInvalidClassName() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$InvalidClassName",
                null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        Assertions.assertThrows(NvaMonitor.NvaMonitorException.class,
            () -> nvaMonitor.start());
    }

    @Test
    void testScheduledMonitorInvalidConstructor() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$ScheduledMonitorInvalidConstructor",
                null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        Assertions.assertThrows(NvaMonitor.NvaMonitorException.class,
            () -> nvaMonitor.start());
    }

    @Test
    void testMonitorExceptionConstructor() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("constructorShouldThrowException", "true");
        settings.put("initShouldCauseInterrupt", "false");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$MonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        Assertions.assertThrows(NvaMonitor.NvaMonitorException.class,
            () -> nvaMonitor.start());
    }

    @Test
    void testAbstractMonitor() {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$AbstractMonitor",
                null));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        Assertions.assertThrows(NvaMonitor.NvaMonitorException.class,
            () -> nvaMonitor.start());
    }

    @Test
    void testMonitorValidConstructor() throws NvaMonitor.NvaMonitorException {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("constructorShouldThrowException", "false");
        settings.put("initShouldCauseInterrupt", "false");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$MonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        nvaMonitor.start();
    }

    @Test
    void testScheduledMonitorValidConstructor() throws NvaMonitor.NvaMonitorException, InterruptedException {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("probeReturnValue", "true");
        settings.put("awaitTime", "2000");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$ScheduledMonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        nvaMonitor.start();
        Thread.sleep(10000);
    }

    @Test
    void testScheduledMonitorExecute() throws NvaMonitor.NvaMonitorException, InterruptedException {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("probeReturnValue", "false");
        settings.put("awaitTime", "2000");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$ScheduledMonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        nvaMonitor.start();
        Thread.sleep(10000);
    }

    @Test
    void testScheduledMonitorShutdown() throws NvaMonitor.NvaMonitorException, InterruptedException {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("probeReturnValue", "true");
        settings.put("awaitTime", "10000");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$ScheduledMonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        nvaMonitor.start();
        Thread.sleep(1000);
        nvaMonitor.stop();
    }

    @Test
    void testMultipleStart() throws Exception {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("probeReturnValue", "true");
        settings.put("awaitTime", "10000");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$ScheduledMonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        nvaMonitor.start();
        Assertions.assertThrows(IllegalStateException.class,
            () -> nvaMonitor.start());
        nvaMonitor.close();
    }

    @Test
    void testStop() throws NvaMonitor.NvaMonitorException {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("constructorShouldThrowException", "false");
        settings.put("initShouldCauseInterrupt", "false");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$MonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        nvaMonitor.start();
        nvaMonitor.stop();
    }

    @Test
    void testCloseWithoutStop() throws Exception {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("constructorShouldThrowException", "false");
        settings.put("initShouldCauseInterrupt", "false");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$MonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        nvaMonitor.start();
        nvaMonitor.close();
    }

    @Test
    void testCloseWithStop() throws Exception {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("constructorShouldThrowException", "false");
        settings.put("initShouldCauseInterrupt", "false");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$MonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        nvaMonitor.start();
        nvaMonitor.stop();
        nvaMonitor.close();
    }

    @Test
    void testMonitorInterrupt() throws Exception {
        List<MonitorConfiguration> monitors = new ArrayList<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("constructorShouldThrowException", "false");
        settings.put("initShouldCauseInterrupt", "true");
        monitors.add(
            new MonitorConfiguration(
                "com.microsoft.azure.practices.nvadaemon.NvaMonitorTest$MonitorValidConstructor",
                settings));
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration(monitors, null);
        NvaMonitor nvaMonitor = new NvaMonitor(daemonConfiguration);
        nvaMonitor.start();
        Thread.sleep(3000);
        nvaMonitor.stop();
        nvaMonitor.close();
    }

    public static class MonitorWithoutValidInterfaces {
        public MonitorWithoutValidInterfaces(MonitorConfiguration monitorConfiguration){
        }
    }

    public abstract static class AbstractMonitor implements Monitor {

        public AbstractMonitor(MonitorConfiguration monitorConfiguration) {

        }

        @Override
        public void init() throws Exception {

        }

        @Override
        public void close() throws Exception {

        }
    }

    public static class ScheduledMonitorInvalidConstructor implements ScheduledMonitor {

        @Override
        public boolean probe() {
            return false;
        }

        @Override
        public void execute() {

        }

        @Override
        public int getTime() {
            return 0;
        }

        @Override
        public TimeUnit getUnit() {
            return null;
        }

        @Override
        public void init() throws Exception {

        }

        @Override
        public void close() throws Exception {

        }
    }

    public static class MonitorValidConstructor implements Monitor {

        public static final String CONSTRUCTOR_SHOULD_THROW_EXCEPTION_KEY =
            "constructorShouldThrowException";
        public static final String INIT_SHOULD_CAUSE_INTERRUPT =
            "initShouldCauseInterrupt";
        public static final String INIT_SHOULD_THROW_EXCEPTION =
            "initShouldThrowException";
        private ExecutorService executorService = Executors.newSingleThreadExecutor();
        private boolean constructorShouldThrowException = false;
        private boolean initShouldCauseInterrupt = false;
        private boolean initShouldThrowException = false;

        public MonitorValidConstructor(MonitorConfiguration monitorConfiguration) {
            Map<String, Object> settings = monitorConfiguration.getSettings();
            if (settings != null) {
                if (settings.containsKey(CONSTRUCTOR_SHOULD_THROW_EXCEPTION_KEY)) {
                    this.constructorShouldThrowException = new Boolean(
                        (String) monitorConfiguration.getSettings()
                            .get(CONSTRUCTOR_SHOULD_THROW_EXCEPTION_KEY));
                }

                if (settings.containsKey(INIT_SHOULD_CAUSE_INTERRUPT)) {
                    this.initShouldCauseInterrupt = new Boolean(
                        (String) monitorConfiguration.getSettings()
                            .get(INIT_SHOULD_CAUSE_INTERRUPT));
                }

                if (settings.containsKey(INIT_SHOULD_THROW_EXCEPTION)) {
                    this.initShouldThrowException = new Boolean(
                        (String)monitorConfiguration.getSettings()
                            .get(INIT_SHOULD_THROW_EXCEPTION));
                }
            }

            if (this.constructorShouldThrowException) {
                throw new RuntimeException("Error creating " +
                    MonitorValidConstructor.class.getName());
            }
        }

        @Override
        public void init() throws Exception {
            if (this.initShouldCauseInterrupt) {
                this.initShouldCauseInterrupt = false;
                Thread currentThread = Thread.currentThread();
                this.executorService.submit(() -> {
                    Thread.sleep(1000);
                    currentThread.interrupt();
                    return null;
                });
            }

            if (this.initShouldThrowException) {
                throw new RuntimeException("Runtime exception in monitor init()");
            }
        }

        @Override
        public void close() throws Exception {
        }
    }

    public static class ScheduledMonitorValidConstructor implements ScheduledMonitor {

        private boolean probeReturnValue;
        private int awaitTime;
        public ScheduledMonitorValidConstructor(MonitorConfiguration monitorConfiguration) {
            this.probeReturnValue = new Boolean(
                (String)monitorConfiguration.getSettings().get("probeReturnValue"));
            this.awaitTime = new Integer(
                (String)monitorConfiguration.getSettings().get("awaitTime"));
        }

        @Override
        public boolean probe() {
            return this.probeReturnValue;
        }

        @Override
        public void execute() {

        }

        @Override
        public int getTime() {
            return this.awaitTime;
        }

        @Override
        public TimeUnit getUnit() {
            return TimeUnit.MILLISECONDS;
        }

        @Override
        public void init() throws Exception {
        }

        @Override
        public void close() throws Exception {
        }
    }
}
