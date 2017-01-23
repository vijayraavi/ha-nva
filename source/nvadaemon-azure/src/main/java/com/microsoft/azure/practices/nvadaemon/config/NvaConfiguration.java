package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.microsoft.azure.management.network.NetworkInterface;
import com.microsoft.azure.practices.nvadaemon.AzureClient;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NvaConfiguration implements ConfigurationValidation {

    private String probeNetworkInterface;
    private int probePort;
    private List<NamedResourceId> networkInterfaces = new ArrayList<>();
    @JsonIgnore
    private SocketAddress probeSocketAddress;

    @JsonCreator
    public NvaConfiguration(@JsonProperty("probeNetworkInterface")String probeNetworkInterface,
                            @JsonProperty("probePort")Integer probePort,
                            @JsonProperty("networkInterfaces")List<NamedResourceId> networkInterfaces) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(probeNetworkInterface),
            "probeNetworkInterface cannot be null or empty");
        Preconditions.checkNotNull(probePort, "probePort must be specified");
        Preconditions.checkArgument(probePort > 0, "probePort must be greater than 0");
        Preconditions.checkNotNull(networkInterfaces, "networkInterfaces cannot be null");
        if (networkInterfaces.size() == 0) {
            throw new IllegalArgumentException("networkInterfaces cannot be empty");
        }

        this.probeNetworkInterface = probeNetworkInterface;
        this.probePort = probePort;
        this.networkInterfaces = networkInterfaces;
        if (this.networkInterfaces.stream()
            .map(n -> n.getName())
            .distinct()
            .count() != this.networkInterfaces.size()) {
            throw new IllegalArgumentException("Duplicate network name found");
        }

        if (this.networkInterfaces.stream()
            .map(n -> n.getId())
            .distinct()
            .count() != this.networkInterfaces.size()) {
            throw new IllegalArgumentException("Duplicate network id found");
        }
    }

    @JsonIgnore
    public SocketAddress getProbeSocketAddress() { return this.probeSocketAddress; }

    public List<NamedResourceId> getNetworkInterfaces() { return this.networkInterfaces; }

    public void validate(AzureClient azureClient) throws ConfigurationException {
        Preconditions.checkNotNull(azureClient, "azureClient cannot be null");

        List<String> invalidNetworkInterfaces = this.networkInterfaces.stream()
            .map(r -> r.getId())
            .filter(id -> !azureClient.checkExistenceById(id))
            .collect(Collectors.toList());
        if (invalidNetworkInterfaces.size() > 0) {
            throw new ConfigurationException("Invalid network interface(s): " +
                invalidNetworkInterfaces.stream().collect(Collectors.joining(", ")));
        }

        // Get the probe network interface and save the private ip
        NetworkInterface probeNetworkInterface =
            azureClient.getNetworkInterfaceById(this.probeNetworkInterface);
        if (probeNetworkInterface == null) {
            throw new ConfigurationException("probeNetworkInterface '" +
                this.probeNetworkInterface + "' does not exist");
        }

        this.probeSocketAddress = new InetSocketAddress(probeNetworkInterface.primaryPrivateIp(),
            this.probePort);
    }
}
