package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.microsoft.azure.practices.nvadaemon.AzureClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AzureProbeMonitorConfiguration implements ConfigurationValidation {
    private static final Logger log = LoggerFactory.getLogger(AzureProbeMonitorConfiguration.class);

    public static final int DEFAULT_NUMBER_OF_FAILURES_THRESHOLD = 3;
    public static final int DEFAULT_PROBE_CONNECT_TIMEOUT = 10000;
    public static final int DEFAULT_PROBE_POLLING_INTERVAL = 3000;

    private List<String> routeTables = new ArrayList<>();
    private List<NamedResourceId> publicIpAddresses = new ArrayList<>();
    @JsonProperty("nvas")
    private List<NvaConfiguration> nvaConfigurations = new ArrayList<>();
    @JsonProperty("azure")
    private AzureConfiguration azureConfiguration;

    private int numberOfFailuresThreshold = DEFAULT_NUMBER_OF_FAILURES_THRESHOLD;
    private int probeConnectTimeout = DEFAULT_PROBE_CONNECT_TIMEOUT;
    private int probePollingInterval = DEFAULT_PROBE_POLLING_INTERVAL;

    public static AzureProbeMonitorConfiguration create(MonitorConfiguration monitorConfiguration)
        throws ConfigurationException {
        Preconditions.checkNotNull(monitorConfiguration,
            "monitorConfiguration cannot be null");
        try {
            ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            AzureProbeMonitorConfiguration configuration = mapper.convertValue(
                monitorConfiguration.getSettings(),
                AzureProbeMonitorConfiguration.class);
            return configuration;
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new ConfigurationException("Error parsing settings", e);
        }
    }

    @JsonCreator
    public AzureProbeMonitorConfiguration(@JsonProperty("azure")AzureConfiguration azureConfiguration,
                                          @JsonProperty("nvas")List<NvaConfiguration> nvaConfigurations,
                                          @JsonProperty("routeTables")List<String> routeTables,
                                          @JsonProperty("publicIpAddresses")List<NamedResourceId> publicIpAddresses,
                                          @JsonProperty("numberOfFailuresThreshold")Integer numberOfFailuresThreshold,
                                          @JsonProperty("probeConnectTimeout")Integer probeConnectTimeout,
                                          @JsonProperty("probePollingInterval")Integer probePollingInterval) {
        this.azureConfiguration = Preconditions.checkNotNull(azureConfiguration,
            "azureConfiguration cannot be null");
        this.nvaConfigurations = Preconditions.checkNotNull(nvaConfigurations,
            "nvaConfigurations cannot be null");

        if (this.nvaConfigurations.size() == 0) {
            throw new IllegalArgumentException("No nva configurations found");
        }

        // Use the first NVA configuration to make sure we don't have misnamed NVA
        // interfaces.
        List<Set<String>> allNetworkInterfaceNames = this.nvaConfigurations.stream()
            .map(c -> c.getNetworkInterfaces().stream()
                .map(ni -> ni.getName())
                .collect(Collectors.toSet()))
            .collect(Collectors.toList());
        Set<String> firstNetworkInterfaceNames = allNetworkInterfaceNames.get(0);
        for (Set<String> networkInterfaceNames : allNetworkInterfaceNames) {
            Set<String> symmetricDifference = Sets.symmetricDifference(
                firstNetworkInterfaceNames, networkInterfaceNames);
            if (!symmetricDifference.isEmpty()) {
                throw new IllegalArgumentException("Resource network interface names mismatch: " +
                    symmetricDifference.stream()
                        .collect(Collectors.joining(", ")));
            }
        }

        if (routeTables != null) {
            this.routeTables = routeTables;
        }

        if (publicIpAddresses != null) {
            this.publicIpAddresses = publicIpAddresses;
        }

        if ((this.routeTables.size() == 0) && (this.publicIpAddresses.size() == 0)) {
            throw new IllegalArgumentException(
                "At least one RouteTable or PublicIpAddress must be configured");
        }

        if (this.publicIpAddresses.stream()
            .map(n -> n.getId())
            .distinct()
            .count() != this.publicIpAddresses.size()) {
            throw new IllegalArgumentException("Duplicate PublicIpAddress id found");
        }

        if (this.publicIpAddresses.stream()
            .map(n -> n.getName())
            .distinct()
            .count() != this.publicIpAddresses.size()) {
            throw new IllegalArgumentException("Duplicate PublicIpAddress name found");
        }

        if (this.routeTables.stream()
            .distinct()
            .count() != this.routeTables.size()) {
            throw new IllegalArgumentException("Duplicate RouteTable found");
        }

        if (numberOfFailuresThreshold != null) {
            this.numberOfFailuresThreshold = numberOfFailuresThreshold;
        }

        if (probeConnectTimeout != null) {
            this.probeConnectTimeout = probeConnectTimeout;
        }

        if (probePollingInterval != null) {
            this.probePollingInterval = probePollingInterval;
        }
    }

    public AzureConfiguration getAzureConfiguration() { return this.azureConfiguration; }

    public int getProbeConnectTimeout() { return this.probeConnectTimeout; }

    public int getProbePollingInterval() { return this.probePollingInterval; }

    public int getNumberOfFailuresThreshold() { return this.numberOfFailuresThreshold; }

    public List<String> getRouteTables() { return this.routeTables; }

    public List<NamedResourceId> getPublicIpAddresses() { return this.publicIpAddresses; }

    public List<NvaConfiguration> getNvaConfigurations() {
        return this.nvaConfigurations;
    }

    public void validate(AzureClient azureClient) throws ConfigurationException {
        Preconditions.checkNotNull(azureClient, "azureClient cannot be null");

        for (NvaConfiguration config : this.nvaConfigurations) {
            config.validate(azureClient);
        }

        if (this.nvaConfigurations.stream()
            .map(c -> ((InetSocketAddress)c.getProbeSocketAddress()).getHostName())
            .distinct()
            .count() != this.nvaConfigurations.size()) {
            throw new ConfigurationException("Duplicate probe IP Address found");
        }

        List<String> invalidPublicIpAddresses = this.publicIpAddresses.stream()
            .map(r -> r.getId())
            .filter(id -> !azureClient.checkExistenceById(id))
            .collect(Collectors.toList());
        if (invalidPublicIpAddresses.size() > 0) {
            throw new ConfigurationException("Invalid public ip address(es): " +
                invalidPublicIpAddresses.stream().collect(Collectors.joining(", ")));
        }

        List<String> invalidRouteTables = this.routeTables.stream()
            .filter(id -> !azureClient.checkExistenceById(id))
            .collect(Collectors.toList());

        if (invalidRouteTables.size() > 0) {
            throw new ConfigurationException("Invalid route table(s): " +
            invalidRouteTables.stream().collect(Collectors.joining(", ")));
        }
    }
}
