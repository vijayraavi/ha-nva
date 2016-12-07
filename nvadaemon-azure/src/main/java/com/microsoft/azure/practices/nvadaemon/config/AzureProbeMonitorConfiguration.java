package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.microsoft.azure.practices.nvadaemon.AzureClient;
import com.microsoft.azure.practices.nvadaemon.OperatingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AzureProbeMonitorConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AzureProbeMonitorConfiguration.class);

    private static final int DEFAULT_PROBE_POLLING_INTERVAL = 3000;
    private static final int DEFAULT_NUMBER_OF_FAILURES_THRESHOLD = 3;
    private static final int DEFAULT_PROBE_CONNECT_TIMEOUT = 10000;

    private String subscriptionId;
    private String clientId;
    private String tenantId;
    private String keyStorePath;
    private String keyStorePassword;
    private String certificatePassword;
    private String publicIpAddress;
    private List<String> routeTables = new ArrayList<>();
    @JsonProperty("nvas")
    private List<NvaConfiguration> nvaConfigurations = new ArrayList<>();

    private int numberOfFailuresThreshold = DEFAULT_NUMBER_OF_FAILURES_THRESHOLD;
    private int probePollingInterval = DEFAULT_PROBE_POLLING_INTERVAL;
    private int probeConnectTimeout = DEFAULT_PROBE_CONNECT_TIMEOUT;


    @JsonIgnore
    private OperatingMode operatingMode;

    public static AzureProbeMonitorConfiguration create(MonitorConfiguration monitorConfiguration)
        throws ConfigurationException {
        ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        AzureProbeMonitorConfiguration configuration = mapper.convertValue(
            Preconditions.checkNotNull(monitorConfiguration,
                "monitorConfiguration cannot be null").getSettings(),
            AzureProbeMonitorConfiguration.class);
        configuration.validate();
        return configuration;
    }

    public AzureProbeMonitorConfiguration() {
    }

    public int getProbeConnectTimeout() { return this.probeConnectTimeout; }

    public int getProbePollingInterval() { return this.probePollingInterval; }

    public int getNumberOfFailuresThreshold() { return this.numberOfFailuresThreshold; }

    public String getSubscriptionId() { return this.subscriptionId; }

    public String getClientId() { return this.clientId; }

    public String getTenantId() { return this.tenantId; }

    public String getPublicIpAddress() { return this.publicIpAddress; }

    public List<String> getRouteTables() { return this.routeTables; }

    public String getKeyStorePath() { return this.keyStorePath; }

    public String getKeyStorePassword() { return this.keyStorePassword; }

    public String getCertificatePassword() { return this.certificatePassword; }

    public OperatingMode getOperatingMode() { return this.operatingMode; }

    private void validate() throws ConfigurationException {
        for (NvaConfiguration nvaConfiguration : this.nvaConfigurations) {
            nvaConfiguration.validate();
        }

        // We need to validate all of the configurations now based on the operating mode.
        // We still need to validate the resources specified in the config with Azure.
        NvaConfiguration first = this.nvaConfigurations.get(0);
        List<NvaConfiguration> invalidConfigurations = this.nvaConfigurations.stream()
            .filter(c -> c.getOperatingMode() != first.getOperatingMode())
            .collect(Collectors.toList());
        if (invalidConfigurations.size() != 0) {
            throw new ConfigurationException(
                "One or more nva configurations are invalid for operating mode " +
                    first.getOperatingMode());
        }

        this.operatingMode = first.getOperatingMode();
    }

    public void validate(AzureClient azureClient)
        throws ConfigurationException {
        Preconditions.checkNotNull(azureClient, "azureClient cannot be null");
        this.validate();

        if (((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_PIP)) &&
            (!azureClient.checkExistenceById(this.publicIpAddress))) {
            throw new IllegalArgumentException("publicIpAddress '" +
                this.publicIpAddress + "' does not exist");
        }

        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_ROUTE)) {
            List<String> invalidRouteTables = this.routeTables.stream()
                .filter(id -> azureClient.getRouteTableById(id) == null)
                .collect(Collectors.toList());
            if (invalidRouteTables.size() > 0) {
                throw new ConfigurationException("Invalid routes in configuration: " +
                invalidRouteTables.stream().collect(Collectors.joining(",")));
            }
        }

        for (NvaConfiguration config : this.nvaConfigurations) {
            config.validateAzureResources(azureClient);
        }
    }

    public List<NvaConfiguration> getNvaConfigurations() {
        return this.nvaConfigurations;
    }
}
