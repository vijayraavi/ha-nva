package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NvaDaemonConfig {
    public static class ConfigException extends Exception {
        public ConfigException(String msg) {
            super(msg);
        }

        public ConfigException(String msg, Exception e) {
            super(msg, e);
        }
    }

    private static final String CONNECTION_STRING_SETTING = "zookeeper.connectionString";
    private static final String MONITOR_CLASS_SETTING = "monitorClass";
    private static final String LEADER_SELECTOR_PATH_SETTING = "zookeeper.leaderSelectorPath";

    private static final Logger LOG = LoggerFactory.getLogger(NvaDaemonConfig.class);

    private ConcurrentHashMap<String, String> settings =
        new ConcurrentHashMap<>();

    //private String connectionString;

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
        OptionGroup optionGroup = new OptionGroup();
        optionGroup.addOption(Option.builder("u")
            .longOpt("updateConfig")
            .argName("updateConfig")
            .hasArg(false)
            .desc("Update the NvaDaemon configuration options in Zookeeper")
            .build());
        optionGroup.addOption(Option.builder("d")
            .longOpt("daemon")
            .argName("daemon")
            .hasArg(false)
            .desc("Run the daemon in command line mode")
            .build());
        optionGroup.isRequired();
        options.addOptionGroup(optionGroup);
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
                LOG.error(stringWriter.toString());
            } finally {
                writer.close();
            }
        }

        return commandLine;
    }

    private void parse(String path) throws ConfigException {
        File configFile = new File(path);

        LOG.info("Reading configuration from: " + path);

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
//        throws IOException {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = entry.getKey().toString().trim();
            String value = entry.getValue().toString().trim();
            //System.out.println(String.format("%1$s=%2$s", key, value));
//            if (key.equals("zookeeper.connectionString")) {
//                connectionString = value;
//            }

            settings.put(key, value);
//            else if (key.startsWith("server.")) {
//                String[] parts = key.split("\\.");
//                // We will probably need to handle qualified server names,
//                // or deeper properties, but this is a test. :)
//                //config.setServerConfigValue(parts[1], parts[2], value);
//            }
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

    public String getConnectionString() {
        //return connectionString;
        return settings.get(CONNECTION_STRING_SETTING);
    }

    public String getMonitorClass() {
        return settings.get(MONITOR_CLASS_SETTING);
    }

    public String getLeaderSelectorPath() {
        return settings.get(LEADER_SELECTOR_PATH_SETTING);
    }

    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(settings);
    }
}