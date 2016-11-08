package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class NvaDaemonConfig {
    private static final Logger log = LoggerFactory.getLogger(NvaDaemonConfig.class);

    public static class ConfigException extends Exception {
        public ConfigException(String msg) {
            super(msg);
        }

        public ConfigException(String msg, Exception e) {
            super(msg, e);
        }
    }

    // Zookeeper specific settings
    private static final String CONNECTION_STRING_SETTING = "zookeeper.connectionString";
    private static final String RETRY_SLEEP_TIME_SETTING = "zookeeper.retrySleepTimeMs";
    private static final String NUMBER_OF_RETRIES_SETTING = "zookeeper.numberOfRetries";
    private static final String LEADER_SELECTOR_PATH_SETTING = "zookeeper.leaderSelectorPath";

    // Daemon specific settings
    private static final String DAEMON_SHUTDOWN_AWAIT_TIME_SETTING = "daemon.shutdownAwaitTimeMs";
    private static final String MONITOR_CLASS_SETTING = "daemon.monitorClass";

    // Zookeeper specific settings defaults
    private static final int DEFAULT_RETRY_SLEEP_TIME_SETTING = 3000;
    private static final int DEFAULT_NUMBER_OF_RETRIES_SETTING = 5;

    // Daemon specific settings defaults
    private static final int DEFAULT_DAEMON_SHUTDOWN_AWAIT_TIME_SETTING = 5000;

    private ConcurrentHashMap<String, String> settings = new ConcurrentHashMap<>();

    public NvaDaemonConfig() {
    }

    public static NvaDaemonConfig parseArguments(String[] args) throws ConfigException {
        CommandLine commandLine = parseCommandLine(args);
        if (commandLine == null) {
            // Invalid options, so just exit
            throw new ConfigException("Error parsing command line arguments");
        }

        return parseConfig(commandLine.getOptionValue("config"));
    }

    private static NvaDaemonConfig parseConfig(String path) throws ConfigException {
        Preconditions.checkNotNull(path, "path cannot be null");
        NvaDaemonConfig cfg = new NvaDaemonConfig();
        cfg.parse(path);
        return cfg;
    }

    private static CommandLine parseCommandLine(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("c")
            .longOpt("config")
            .argName("config")
            .hasArg()
            .required()
            .desc("Path to configuration file")
            .build());
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            // Not sure we need to do this.
            HelpFormatter formatter = new HelpFormatter();
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            try {
                formatter.printHelp(writer, 74, "nvadaemon", null, options, 1, 3, null, false);
                log.error(stringWriter.toString());
            } finally {
                writer.close();
            }
        }

        return commandLine;
    }

    private void parse(String path) throws ConfigException {
        File configFile = new File(path);

        log.info("Reading configuration from: " + path);

        try {
            if (!configFile.exists()) {
                throw new IllegalArgumentException(configFile.toString() +
                    " file is missing");
            }

            Properties cfg = new Properties();
            FileInputStream in = new FileInputStream(configFile);
            try {
                cfg.load(in);
            } finally {
                in.close();
            }

            parseProperties(cfg);
        } catch (IOException | IllegalArgumentException e) {
            throw new ConfigException("Error processing " + path, e);
        }
    }

    private void parseProperties(Properties properties) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = entry.getKey().toString().trim();
            String value = entry.getValue().toString().trim();
            settings.put(key, value);
        }

        // Validate
        validateSetting(CONNECTION_STRING_SETTING);
        validateSetting(LEADER_SELECTOR_PATH_SETTING);
        validateSetting(MONITOR_CLASS_SETTING);
    }

    private void validateSetting(String settingName) throws IllegalArgumentException {
        String value = null;
        if (settings.containsKey(settingName)) {
            value = settings.get(settingName);
        }

        if ((value == null) || (value.length() == 0)) {
            throw new IllegalArgumentException("Invalid or missing setting: " + settingName);
        }
    }

    private int getIntSetting(String settingName, int defaultValue) {
        int settingValue = defaultValue;
        if (settings.containsKey(settingName)) {
            try {
                String settingValueString = settings.get(settingName);
                Integer integer = new Integer(settingValueString);
                settingValue = integer.intValue();
            } catch (NumberFormatException e) {
                log.warn("Invalid value for " + settingName + ": " + settings.get(settingName), e);
                log.info("Using default value for " + settingName + ": " + defaultValue);
            }
        }

        return settingValue;
    }

    public String getConnectionString() {
        return settings.get(CONNECTION_STRING_SETTING);
    }

    public String getMonitorClass() {
        return settings.get(MONITOR_CLASS_SETTING);
    }

    public String getLeaderSelectorPath() {
        return settings.get(LEADER_SELECTOR_PATH_SETTING);
    }

    public int getRetrySleepTime() {
        return getIntSetting(RETRY_SLEEP_TIME_SETTING, DEFAULT_RETRY_SLEEP_TIME_SETTING);
    }

    public int getNumberOfRetries() {
        return getIntSetting(NUMBER_OF_RETRIES_SETTING, DEFAULT_NUMBER_OF_RETRIES_SETTING);
    }

    public int getDaemonShutdownAwaitTime() {
        return getIntSetting(DAEMON_SHUTDOWN_AWAIT_TIME_SETTING, DEFAULT_DAEMON_SHUTDOWN_AWAIT_TIME_SETTING);
    }

    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(settings);
    }
}