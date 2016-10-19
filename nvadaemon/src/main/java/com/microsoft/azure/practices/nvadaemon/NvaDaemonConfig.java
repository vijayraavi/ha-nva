package com.microsoft.azure.practices.nvadaemon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class NvaDaemonConfig {
    public static class ConfigException extends Exception {
        public ConfigException(String msg) {
            super(msg);
        }

        public ConfigException(String msg, Exception e) {
            super(msg, e);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NvaDaemonConfig.class);

    private String connectionString;

    public NvaDaemonConfig() {
    }

    public void parse(String path) throws ConfigException {
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

    private void parseProperties(Properties properties)
        throws IOException, ConfigException {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = entry.getKey().toString().trim();
            String value = entry.getValue().toString().trim();
            //System.out.println(String.format("%1$s=%2$s", key, value));
            if (key.equals("zookeeper.connectionString")) {
                connectionString = value;
            }
//            else if (key.startsWith("server.")) {
//                String[] parts = key.split("\\.");
//                // We will probably need to handle qualified server names,
//                // or deeper properties, but this is a test. :)
//                //config.setServerConfigValue(parts[1], parts[2], value);
//            }
        }

        // Validate
        // Should we check for an empty string?
        if (connectionString == null) {
            throw new IllegalArgumentException("connectionString is not set");
        }
    }

    public String getConnectionString() {
        return connectionString;
    }
}