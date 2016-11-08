package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.RestClient;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.network.*;
import com.microsoft.azure.practices.nvadaemon.monitor.ScheduledMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AzureProbeMonitor implements ScheduledMonitor {

    private final Logger log = LoggerFactory.getLogger(AzureProbeMonitor.class);
    private static final String CLIENT_ID_SETTING="azure.clientId";
    private static final String TENANT_ID_SETTING="azure.tenantId";
    private static final String CLIENT_SECRET_SETTING="azure.clientSecret";
    private static final String PROBE_IP_ADDRESS="probe.ipAddress";
    private static final String PROBE_PORT="probe.port";
    private static final String PROBE_POLLING_INTERVAL="probe.pollingIntervalMs";
    private static final String NUMBER_OF_FAILURES_THRESHOLD="probe.numberOfFailuresThreshold";
    private static final String NVA_PUBLIC_IP_ADDRESS="probe.publicIpAddress";
    private static final String NVA_ROUTE_TABLE="probe.routeTable";
    private static final String NVA_ROUTE_TABLE_ROUTE="probe.routeTableRoute";
    private final ArrayList<NvaNetworkConfig> networkConfigurations = new ArrayList<>();

    private static int DEFAULT_NUMBER_OF_FAILURES_THRESHOLD = 3;
    private static int DEFAULT_PROBE_POLLING_INTERVAL = 3000;

    private int failures;
    private Map<String, String> config;
    private SocketChannel channel;
    private Azure azure;
    private RestClient restClient;

    private int currentNvaIndex = 0;
    private PublicIpAddress publicIpAddress = null;
    private RouteTable routeTable = null;
    private OperatingMode operatingMode;
    private int probePollingInterval;
    private int numberofFailuresThreshold;

    private static final class NvaNetworkConfig {
        private String publicIpNetworkInterface;
        private String routeNetworkInterface;
        private String probeNetworkInterface;
        private String probeNetworkInterfacePrivateIpAddress;

        private NvaNetworkConfig(String publicIpNetworkInterface, String routeNetworkInterface,
                                 String probeNetworkInterface, String probeNetworkInterfacePrivateIpAddress) {
            Preconditions.checkNotNull(probeNetworkInterface, "probeNetworkInterface cannot be null");
            Preconditions.checkNotNull(probeNetworkInterfacePrivateIpAddress,
                "probeNetworkInterfacePrivateIpAddress cannot be null");
            // This will only be invalid if both are null.
            if ((publicIpNetworkInterface == null) && (routeNetworkInterface == null)) {
                throw new IllegalArgumentException(
                    "publicIpNetworkInterface and routeNetworkInterface cannot both be null");
            }
            this.publicIpNetworkInterface = publicIpNetworkInterface;
            this.routeNetworkInterface = routeNetworkInterface;
            this.probeNetworkInterface = probeNetworkInterface;
            this.probeNetworkInterfacePrivateIpAddress = probeNetworkInterfacePrivateIpAddress;
        }

        private String getPublicIpNetworkInterface() { return this.publicIpNetworkInterface; }

        private String getRouteNetworkInterface() { return this.routeNetworkInterface; }

        private String getProbeNetworkInterface() { return this.probeNetworkInterface; }

        private String getProbeNetworkInterfacePrivateIpAddress() { return this.probeNetworkInterfacePrivateIpAddress; }

        private static NvaNetworkConfig create(OperatingMode operatingMode,
            String publicIpNetworkInterface, String routeNetworkInterface,
                                               String probeNetworkInterface,
                                               String probeNetworkInterfacePrivateIpAddress) {
            if (((operatingMode == OperatingMode.PIP_AND_ROUTE) &&
                ((publicIpNetworkInterface == null) || (routeNetworkInterface == null))) ||
                ((operatingMode == OperatingMode.ONLY_PIP) && (publicIpNetworkInterface == null)) ||
                ((operatingMode == OperatingMode.ONLY_ROUTE) && (routeNetworkInterface == null))){
                throw new IllegalArgumentException("Invalid configuration entry.  OperatingMode: " + operatingMode +
                " publicIpNetworkInterface: " + publicIpNetworkInterface + " routeNetworkInterface: " +
                routeNetworkInterface);
            }

            return new NvaNetworkConfig(publicIpNetworkInterface, routeNetworkInterface,
                probeNetworkInterface, probeNetworkInterfacePrivateIpAddress);
        }
    }

    private enum OperatingMode {
        PIP_AND_ROUTE,
        ONLY_PIP,
        ONLY_ROUTE
    }

    private int indexOfPublicIpNetworkInterface(String name) {
        Preconditions.checkNotNull(name, "name cannot be null");
        for (int i = 0; i < networkConfigurations.size(); i++) {
            if (name.equals(networkConfigurations.get(i).getPublicIpNetworkInterface())) {
                return i;
            }
        }

        return -1;
    }

    private int indexOfRouteNetworkInterface(String privateIpAddress) {
        Preconditions.checkNotNull(privateIpAddress, "privateIpAddress cannot be null");
        for (int i = 0; i < networkConfigurations.size(); i++) {
            // See if we can do this without getting each interface.
            NetworkInterface routeNetworkInterface = azure.networkInterfaces()
                .getById(
                //.getByGroup(resourceGroupName,
                    networkConfigurations.get(i).getRouteNetworkInterface());
            if (privateIpAddress.equals(routeNetworkInterface.primaryPrivateIp())) {
                return i;
            }
        }

        return -1;
    }

    private OperatingMode deduceOperatingMode(String prefix) {
        Preconditions.checkNotNull(prefix, "prefix cannot be null");
        // We essentially have three modes of operation.  Rather than making the user specify
        // the mode, we can extrapolate from the configuration entries.  We will drive this
        // based on the first NVA entry for the probe.  We will validate the following entries
        // with respect to the first one.
        // Mode #1 - publicIpAddressNetworkInterface and routeNetworkInterface are specified.
        // Mode #2 - Only publicIpAddressNetworkInterface is specified
        // Mode #3 - Only routeNetworkInterface is specified.
        String publicIpNetworkInterface = this.config.get(prefix + ".publicIpNetworkInterface");
        String routeNetworkInterface = this.config.get(prefix + ".routeNetworkInterface");
        if ((publicIpNetworkInterface != null) && (routeNetworkInterface != null)) {
            return OperatingMode.PIP_AND_ROUTE;
        } else if ((publicIpNetworkInterface != null) && (routeNetworkInterface != null)) {
            return OperatingMode.ONLY_PIP;
        } else if ((publicIpNetworkInterface == null) && (routeNetworkInterface != null)) {
            return OperatingMode.ONLY_ROUTE;
        } else {
            throw new IllegalArgumentException("Invalid NVA configuration entry.");
        }
    }

    private void readConfiguration() {

        this.numberofFailuresThreshold = DEFAULT_NUMBER_OF_FAILURES_THRESHOLD;
        if (this.config.containsKey(NUMBER_OF_FAILURES_THRESHOLD)) {
            try {
                String value = this.config.get(NUMBER_OF_FAILURES_THRESHOLD);
                Integer integerValue = new Integer(value);
                this.numberofFailuresThreshold = integerValue.intValue();
            } catch (NumberFormatException e) {
                log.warn("Invalid value for " + NUMBER_OF_FAILURES_THRESHOLD + ": " +
                    this.config.get(NUMBER_OF_FAILURES_THRESHOLD), e);
                log.info("Using default value for " + NUMBER_OF_FAILURES_THRESHOLD + ": " +
                    DEFAULT_NUMBER_OF_FAILURES_THRESHOLD);
            }
        }

        this.probePollingInterval = DEFAULT_PROBE_POLLING_INTERVAL;
        if (this.config.containsKey(PROBE_POLLING_INTERVAL)) {
            try {
                String value = this.config.get(PROBE_POLLING_INTERVAL);
                Integer integerValue = new Integer(value);
                this.probePollingInterval = integerValue.intValue();
            } catch (NumberFormatException e) {
                log.warn("Invalid value for " + PROBE_POLLING_INTERVAL + ": " +
                    this.config.get(PROBE_POLLING_INTERVAL), e);
                log.info("Using default value for " + PROBE_POLLING_INTERVAL + ": " +
                    DEFAULT_PROBE_POLLING_INTERVAL);
            }
        }

        ArrayList<String> prefixes = new ArrayList<>();
        for (String key : this.config.keySet()) {
            if (key.startsWith("probe.nva")) {
                String prefix = key.substring(0, key.lastIndexOf("."));
                if (!prefixes.contains(prefix)) {
                    prefixes.add(prefix);
                }
            }
        }

        if (prefixes.size() == 0) {
            throw new IllegalArgumentException("No NVA configuration entries found");
        }

        this.operatingMode = deduceOperatingMode(prefixes.get(0));
        for (String prefix : prefixes) {
            // For safe operation, we need to check the probing network interfaces to make sure
            // they are valid.  If not, we need to throw an exception.
            String probeNetworkInterfaceName = this.config.get(prefix + ".probeNetworkInterface");
            NetworkInterface probeNetworkInterface = this.azure.networkInterfaces()
                .getById(probeNetworkInterfaceName);
                //.getByGroup(this.config.get(AZURE_RESOURCE_GROUP), probeNetworkInterfaceName);
            if (probeNetworkInterface == null) {
                throw new IllegalArgumentException("probeNetworkInterface " +
                    probeNetworkInterfaceName + " was not found");
            }

            this.networkConfigurations.add(NvaNetworkConfig.create(this.operatingMode,
                this.config.get(prefix + ".publicIpNetworkInterface"),
                this.config.get(prefix + ".routeNetworkInterface"),
                probeNetworkInterfaceName, probeNetworkInterface.primaryPrivateIp()
            ));
        }
    }

    private void initializeAzure() throws Exception {
//        AzureTokenCredentials credentials = new ApplicationTokenCredentials(
//            config.get(CLIENT_ID_SETTING),
//            config.get(TENANT_ID_SETTING),
//            config.get(CLIENT_SECRET_SETTING),
//            AzureEnvironment.AZURE
//        );
        try {
            CertificateCredentials certificateCredentials = new CertificateCredentials(
                config.get(CLIENT_ID_SETTING), config.get(TENANT_ID_SETTING),
                AzureEnvironment.AZURE, this.config
            );
            this.restClient = certificateCredentials
                .getEnvironment()
                .newRestClientBuilder()
                .withCredentials(certificateCredentials)
                .build();
            azure = Azure.authenticate(this.restClient, certificateCredentials.getDomain())
                .withDefaultSubscription();
        } catch (CloudException | IOException e) {
            log.error("Exception creating Azure client", e);
            throw e;
        }
    }

    private void getCurrentNva() {
        // We need to find out the current setup of the NVAs
        int pipNetworkInterfaceIndex = -1;
        int routeNetworkInterfaceIndex = -1;
        String pipName = this.config.get(NVA_PUBLIC_IP_ADDRESS);
        String routeTableName = this.config.get(NVA_ROUTE_TABLE);
        String routeName = this.config.get(NVA_ROUTE_TABLE_ROUTE);
        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_PIP)) {
            PublicIpAddress publicIpAddress = azure.publicIpAddresses().getById(pipName);
            if (publicIpAddress == null) {
                throw new IllegalArgumentException("Invalid PublicIpAddress name: " + pipName);
            }

            if (!publicIpAddress.hasAssignedNetworkInterface()) {
                throw new IllegalArgumentException(
                    "PublicIpAddress " + pipName + " is not assigned to a NetworkInterface");
            }

            NicIpConfiguration nicIpConfiguration =
                publicIpAddress.getAssignedNetworkInterfaceIpConfiguration();

            NetworkInterface networkInterface = nicIpConfiguration.parent();
            log.debug("NetworkInterface: " + networkInterface.id() + " PublicIpAddress: " + pipName);
            pipNetworkInterfaceIndex = indexOfPublicIpNetworkInterface(networkInterface.id());
            if (pipNetworkInterfaceIndex == -1) {
                throw new IllegalArgumentException("NetworkInterface " + networkInterface.id() +
                    " was not found in the list of valid network interfaces");
            }

            this.publicIpAddress = publicIpAddress;
            // If everything goes okay, this will be valid
            this.currentNvaIndex = pipNetworkInterfaceIndex;
        }

        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_ROUTE)) {
            RouteTable routeTable = azure.routeTables().getById(//azure.routeTables().getByGroup(resourceGroupName,
                routeTableName);
            if (routeTable == null) {
                throw new IllegalArgumentException("Invalid RouteTable name: " + routeTableName);
            }

            Route route = routeTable.routes().get(routeName);
            if (route == null) {
                throw new IllegalArgumentException("Invalid Route name: " + routeName);
            }

            routeNetworkInterfaceIndex = indexOfRouteNetworkInterface(route.nextHopIpAddress());
            if (routeNetworkInterfaceIndex == -1) {
                throw new IllegalArgumentException("NetworkInterface for route " + route.inner().id() +
                    " was not found in the list of valid network interfaces");
            }

            this.routeTable = routeTable;
            // If everything goes okay, this will be valid
            this.currentNvaIndex = routeNetworkInterfaceIndex;
        }

        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) &&
            (pipNetworkInterfaceIndex != routeNetworkInterfaceIndex)) {
            // The indexes need to match here.  Otherwise, the pip and route are pointing at different NVAs
            // and the currentNvaIndex is wrong
            throw new IllegalArgumentException("Current PublicIpAddress and Route point to different NVAs");
        }
    }

    private void migrateAzureResources() {
        int nextNvaIndex = (this.currentNvaIndex + 1) % this.networkConfigurations.size();
        NvaNetworkConfig currentNvaNetworkConfig =
            this.networkConfigurations.get(this.currentNvaIndex);
        NvaNetworkConfig nextNvaNetworkConfig = this.networkConfigurations.get(nextNvaIndex);
        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_PIP)) {
            migratePublicIpAddress(currentNvaNetworkConfig, nextNvaNetworkConfig);
        }

        if ((this.operatingMode == OperatingMode.PIP_AND_ROUTE) ||
            (this.operatingMode == OperatingMode.ONLY_ROUTE)) {
            migrateRoute(currentNvaNetworkConfig, nextNvaNetworkConfig);
        }

        this.currentNvaIndex = nextNvaIndex;
    }

    private void migrateRoute(NvaNetworkConfig fromNvaNetworkConfig,
                              NvaNetworkConfig toNvaNetworkConfig) {
        Preconditions.checkNotNull(fromNvaNetworkConfig, "fromNvaNetworkConfig cannot be null");
        Preconditions.checkNotNull(toNvaNetworkConfig, "toNvaNetworkConfig cannot be null");
        NetworkInterface fromNetworkInterface = null;
        NetworkInterface toNetworkInterface = null;

        log.debug("Getting network interface " + fromNvaNetworkConfig.getRouteNetworkInterface());
        fromNetworkInterface = this.azure.networkInterfaces().getById(
            fromNvaNetworkConfig.getRouteNetworkInterface());
        log.debug("Got network interface " + fromNvaNetworkConfig.getRouteNetworkInterface());

        if (fromNetworkInterface == null) {
            throw new IllegalArgumentException("Error getting from network interface: " +
                fromNvaNetworkConfig.getRouteNetworkInterface());
        }

        log.debug("Getting network interface " + toNvaNetworkConfig.getRouteNetworkInterface());
        toNetworkInterface = this.azure.networkInterfaces().getById(
            toNvaNetworkConfig.getRouteNetworkInterface());
        log.debug("Got network interface " + toNvaNetworkConfig.getRouteNetworkInterface());
        if (toNetworkInterface == null) {
            throw new IllegalArgumentException("Error getting to network interface: " +
                toNvaNetworkConfig.getRouteNetworkInterface());
        }

        // Update the route table
        log.debug("Updating route " + this.config.get(NVA_ROUTE_TABLE_ROUTE) +
            " to " + toNetworkInterface.primaryPrivateIp());
        routeTable.update()
            .updateRoute(this.config.get(NVA_ROUTE_TABLE_ROUTE))
            .withNextHopToVirtualAppliance(toNetworkInterface.primaryPrivateIp())
            .parent()
            .apply();
        log.debug("Updated route " + this.config.get(NVA_ROUTE_TABLE_ROUTE) +
            " to " + toNetworkInterface.primaryPrivateIp());
    }

    private void migratePublicIpAddress(NvaNetworkConfig fromNvaNetworkConfig,
                                        NvaNetworkConfig toNvaNetworkConfig) {
        Preconditions.checkNotNull(fromNvaNetworkConfig, "fromNvaNetworkConfig cannot be null");
        Preconditions.checkNotNull(toNvaNetworkConfig, "toNvaNetworkConfig cannot be null");
        NetworkInterface fromNetworkInterface = null;
        NetworkInterface toNetworkInterface = null;

        log.debug("Getting network interface " + fromNvaNetworkConfig.getPublicIpNetworkInterface());
        fromNetworkInterface = this.azure.networkInterfaces().getById(
            fromNvaNetworkConfig.getPublicIpNetworkInterface());
        log.debug("Got network interface " + fromNvaNetworkConfig.getPublicIpNetworkInterface());

        if (fromNetworkInterface == null) {
            throw new IllegalArgumentException("Error getting from network interface: " +
                fromNvaNetworkConfig.getPublicIpNetworkInterface());
        }

        log.debug("Getting network interface " + toNvaNetworkConfig.getPublicIpNetworkInterface());
        toNetworkInterface = this.azure.networkInterfaces().getById(
            toNvaNetworkConfig.getPublicIpNetworkInterface());
        log.debug("Got network interface " + toNvaNetworkConfig.getPublicIpNetworkInterface());
        if (toNetworkInterface == null) {
            throw new IllegalArgumentException("Error getting to network interface: " +
                toNvaNetworkConfig.getPublicIpNetworkInterface());
        }

        log.debug("Removing public ip address from network interface " + fromNetworkInterface.id());
        // Swap the pip
        fromNetworkInterface.update()
            .withoutPrimaryPublicIpAddress()
            .apply();
        log.debug("Public ip address removed from network interface " + fromNetworkInterface.id());

        log.debug("Adding public ip address to network interface " + toNetworkInterface.id());
        toNetworkInterface.update()
            .withExistingPrimaryPublicIpAddress(this.publicIpAddress)
            .apply();
        log.debug("Added public ip address to network interface " + toNetworkInterface.id());
    }

    @Override
    public void init(Map<String, String> config) throws Exception {
        Preconditions.checkNotNull(config, "config cannot be null");
        this.config = config;
        failures = 0;
        initializeAzure();
        readConfiguration();
        getCurrentNva();
    }

    @Override
    public boolean probe() {
        try {
            NvaNetworkConfig currentNvaConfig = this.networkConfigurations.get(this.currentNvaIndex);
            channel = SocketChannel.open();
            channel.connect(new InetSocketAddress(
                currentNvaConfig.getProbeNetworkInterfacePrivateIpAddress(),
                new Integer(this.config.get(PROBE_PORT))));
                //this.config.get(PROBE_IP_ADDRESS), new Integer(this.config.get(PROBE_PORT))));
            channel.close();
            // If this works, we want to reset any previous failures.
            failures = 0;
        } catch (IOException e) {
            log.info("probe() threw an exception", e);
            failures++;
        }

        return failures < this.numberofFailuresThreshold;
    }

    @Override
    public void execute() {
        log.info("Probe failure.  Executing failure action.");
        migrateAzureResources();
        failures = 0;
    }

    @Override
    public int getTime() {
        return this.probePollingInterval;
    }

    @Override
    public TimeUnit getUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    public void close() throws IOException {
        // This is needed to work around an okio threading issue.  By shutting it
        // down manually here, it only takes one minute for okio to shutdown the idle connection thread.
        restClient.httpClient().dispatcher().executorService().shutdown();
        try {
            if (!restClient
                .httpClient()
                .dispatcher()
                .executorService()
                .awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                // We have been waiting five seconds and the executorService hasn't shutdown, so
                // try to force it.
                restClient.httpClient().dispatcher().executorService().shutdownNow();
            }
        } catch (InterruptedException e) {
            log.debug("AzureProbeMonitor.close() interrupted");
            Thread.currentThread().interrupt();
        }

        restClient.httpClient().connectionPool().evictAll();
    }
}
