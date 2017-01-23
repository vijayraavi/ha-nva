package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;

public class NvaDaemonConfiguration {
    private static final Logger log = LoggerFactory.getLogger(NvaDaemonConfiguration.class);

    private ZookeeperConfiguration zookeeperConfiguration;
    private DaemonConfiguration daemonConfiguration;

    @JsonCreator
    public NvaDaemonConfiguration(@JsonProperty("zookeeper")ZookeeperConfiguration zookeeperConfiguration,
                                  @JsonProperty("daemon")DaemonConfiguration daemonConfiguration) {
        this.zookeeperConfiguration = Preconditions.checkNotNull(zookeeperConfiguration,
            "zookeeperConfiguration cannot be null");
        this.daemonConfiguration = Preconditions.checkNotNull(daemonConfiguration,
            "daemonConfiguration cannot be null");
    }

    public ZookeeperConfiguration getZookeeperConfiguration() { return this.zookeeperConfiguration; }

    public DaemonConfiguration getDaemonConfiguration() { return this.daemonConfiguration; }

    public static NvaDaemonConfiguration parseConfig(Reader reader) throws ConfigurationException {
        Preconditions.checkNotNull(reader, "reader cannot be null");
        try {
            ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            NvaDaemonConfiguration configuration =
                mapper.readValue(reader, NvaDaemonConfiguration.class);
            return configuration;
        } catch (IOException ioe) {
            throw new ConfigurationException("Error reading configuration", ioe);
        }
    }
}
