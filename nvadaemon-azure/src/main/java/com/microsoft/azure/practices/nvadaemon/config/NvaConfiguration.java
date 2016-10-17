package com.microsoft.azure.practices.nvadaemon.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.microsoft.azure.management.network.NetworkInterface;
import com.microsoft.azure.practices.nvadaemon.AzureClient;
import com.microsoft.azure.practices.nvadaemon.OperatingMode;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class NvaConfiguration {

    private String publicIpNetworkInterface;
    private String routeNetworkInterface;
    private String probeNetworkInterface;
    private int probePort;
    private String probeNetworkInterfacePrivateIpAddress;
    private SocketAddress probeSocketAddress;
    @JsonIgnore
    private OperatingMode operatingMode;

    NvaConfiguration() {
    }

    public String getPublicIpNetworkInterface() { return this.publicIpNetworkInterface; }

    public String getRouteNetworkInterface() { return this.routeNetworkInterface; }

    public String getProbeNetworkInterface() { return this.probeNetworkInterface; }

    public String getProbeNetworkInterfacePrivateIpAddress() { return this.probeNetworkInterfacePrivateIpAddress; }

    public int getProbePort() { return this.probePort; }

    public OperatingMode getOperatingMode() { return this.operatingMode; }

    public SocketAddress getProbeSocketAddress() { return this.probeSocketAddress; }

    private OperatingMode deduceOperatingMode() {
        if ((publicIpNetworkInterface != null) && (routeNetworkInterface != null)) {
            return OperatingMode.PIP_AND_ROUTE;
        } else if ((publicIpNetworkInterface != null) && (routeNetworkInterface == null)) {
            return OperatingMode.ONLY_PIP;
        } else if ((publicIpNetworkInterface == null) && (routeNetworkInterface != null)) {
            return OperatingMode.ONLY_ROUTE;
        } else {
            throw new IllegalArgumentException("Could not deduce operating mode");
        }
    }

    public void validate() throws ConfigurationException {
        if ((Strings.isNullOrEmpty(publicIpNetworkInterface)) &&
            (Strings.isNullOrEmpty(routeNetworkInterface))) {
            throw new ConfigurationException(
                "publicIpNetworkInterface and routeNetworkInterface cannot both be null or empty");
        }

        this.operatingMode = this.deduceOperatingMode();
    }

    void validateAzureResources(AzureClient azureClient) {
        Preconditions.checkNotNull(azureClient, "azureClient cannot be null");
        if (((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_PIP)) &&
            (!azureClient.checkExistenceById(this.publicIpNetworkInterface))) {
            throw new IllegalArgumentException("publicIpNetworkInterface '" +
            this.publicIpNetworkInterface + "' does not exist");
        }

        if (((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_ROUTE)) &&
            (!azureClient.checkExistenceById(this.routeNetworkInterface))) {
            throw new IllegalArgumentException("routeNetworkInterface '" +
                this.routeNetworkInterface + "' does not exist");
        }

        // Get the probe network interface and save the private ip
        NetworkInterface probeNetworkInterface =
            azureClient.getNetworkInterfaceById(this.probeNetworkInterface);
        if (probeNetworkInterface == null) {
            throw new IllegalArgumentException("probeNetworkInterface '" +
                this.probeNetworkInterface + "' does not exist");
        }

        this.probeNetworkInterfacePrivateIpAddress = probeNetworkInterface.primaryPrivateIp();
        this.probeSocketAddress = new InetSocketAddress(probeNetworkInterface.primaryPrivateIp(),
            this.probePort);
    }
}
