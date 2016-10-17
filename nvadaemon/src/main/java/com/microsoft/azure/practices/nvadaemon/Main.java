package com.microsoft.azure.practices.nvadaemon;


import org.apache.commons.cli.*;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.microsoft.azure.practices.nvadaemon.NvaDaemonConfig.ConfigException;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private CuratorFramework client;
    private NvaDaemonConfig config;
    private static final String ZK_NAMESPACE = "nvadaemon";

    protected void close() {
        client.close();
    }
    protected void initialize(String[] args) throws ConfigException {
        CommandLine commandLine = parseOptions(args);
        if (commandLine == null) {
            // Invalid options, so just exit
            System.exit(1);
        }

        this.config = parseConfig(commandLine.getOptionValue("config"));
        try {
            createClient(this.config);
        }
        catch (InterruptedException e)
        {
            LOG.error(e.getMessage());
        }

        if (commandLine.hasOption("daemon")) {
            try {
                runDaemon();
            }
            catch (Exception e) {
                LOG.error("Error running daemon", e);
            }
        }
    }

    protected NvaDaemonConfig parseConfig(String path)
        throws ConfigException {
        NvaDaemonConfig cfg = new NvaDaemonConfig();
        try {
            if (path == null) {
                throw new IllegalArgumentException("Value for path must be provided");
            }

            cfg.parse(path);
        }
        catch (ConfigException e) {
            LOG.error(e.getMessage());
            throw e;
        }

        return cfg;
    }

    private void createClient(NvaDaemonConfig config) throws InterruptedException {
        LOG.debug("Creating CuratorFramework");
        client = CuratorFrameworkFactory.builder()
            .connectString(config.getConnectionString())
            .retryPolicy(new ExponentialBackoffRetry(1000, 10))
            .namespace(ZK_NAMESPACE)
            .build();
        LOG.debug("Starting CuratorFramework");
        client.start();
        LOG.debug("Waiting for Zookeeper connection");
        client.blockUntilConnected();
        LOG.debug("Connected to Zookeeper");
    }

    private static CommandLine parseOptions(String[] args) {
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
        }
        catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("nvadaemon", options);
        }

        return commandLine;
    }

    private void runDaemon() throws Exception {
        DaemonContext context = new DaemonContext() {
            public DaemonController getController() {
                return null;
            }

            public String[] getArguments() {
                return new String[0];
            }
        };

        NvaDaemon daemon = new NvaDaemon(config, client);
        try {
            daemon.init(context);
            daemon.start();
            System.out.println("Press enter to quit...");
            new BufferedReader(new InputStreamReader(System.in)).readLine();
        }
        catch (Exception e)
        {
            LOG.error("Unexpected exception, exiting abnormally", e);
            throw e;
        }
        finally {
            daemon.stop();
            daemon.destroy();
        }
    }
    public static void main(final String[] args) throws Exception {
        Main main = new Main();
        main.initialize(args);
        Thread.sleep(5000);
        main.close();
    }
}
