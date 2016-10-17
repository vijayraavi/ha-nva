package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class NvaDaemonConfiguration {
    private static final Logger log = LoggerFactory.getLogger(NvaDaemonConfiguration.class);

    @JsonProperty("zookeeper")
    private ZookeeperConfiguration zookeeperConfiguration;

    @JsonProperty("daemon")
    private DaemonConfiguration daemonConfiguration;

    public NvaDaemonConfiguration() {
    }

    public ZookeeperConfiguration getZookeeperConfiguration() { return this.zookeeperConfiguration; }

    public DaemonConfiguration getDaemonConfiguration() { return this.daemonConfiguration; }

    public void validate() throws ConfigurationException {
        try {
            Preconditions.checkNotNull(this.zookeeperConfiguration,
                "zookeeperConfiguration cannot be null");
            Preconditions.checkNotNull(this.daemonConfiguration,
                "daemonConfiguration cannot be null");
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("NvaDaemonConfiguration error", e);
        }

        this.zookeeperConfiguration.validate();
        this.daemonConfiguration.validate();
    }
    public static NvaDaemonConfiguration parseArguments(String[] args) throws ConfigurationException {
        CommandLine commandLine = parseCommandLine(args);
        if (commandLine == null) {
            // Invalid options, so just exit
            throw new ConfigurationException("Error parsing command line arguments");
        }

        return parseConfig(commandLine.getOptionValue("config"));
    }

    private static NvaDaemonConfiguration parseConfig(String path) throws ConfigurationException {
        try {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(path), "path cannot be null or empty");
            log.info("Reading configuration from: " + path);
            ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            NvaDaemonConfiguration configuration =
                mapper.readValue(new File(path), NvaDaemonConfiguration.class);
            configuration.validate();
            return configuration;
        } catch (IOException | IllegalArgumentException e) {
            throw new ConfigurationException("Error processing " + path, e);
        }
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
}
