package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.network.*;
import com.microsoft.azure.practices.nvadaemon.collect.CurrentPeekingIterator;
import com.microsoft.azure.practices.nvadaemon.config.AzureProbeMonitorConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.MonitorConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.NvaConfiguration;
import com.microsoft.azure.practices.nvadaemon.credentials.AsymmetricKeyCredentialFactory;
import com.microsoft.azure.practices.nvadaemon.credentials.AzureClientIdCertificateCredentialFactoryImpl;
import com.microsoft.azure.practices.nvadaemon.credentials.CertificateCredentials;
import com.microsoft.azure.practices.nvadaemon.monitor.ScheduledMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AzureProbeMonitor implements ScheduledMonitor {

    private final Logger log = LoggerFactory.getLogger(AzureProbeMonitor.class);
    private int failures;
    //private SocketChannel channel;
    private AzureClient azureClient;
    private AzureProbeMonitorConfiguration configuration;
    private CurrentPeekingIterator<NvaConfiguration> nvaConfigurations;

    private void initializeAzure() throws CloudException {
        try {
            AsymmetricKeyCredentialFactory factory =
                new AzureClientIdCertificateCredentialFactoryImpl(
                    this.configuration);

            CertificateCredentials certificateCredentials = new CertificateCredentials(
                this.configuration.getTenantId(), AzureEnvironment.AZURE, factory);
            this.azureClient = AzureClient.create(certificateCredentials,
                this.configuration.getSubscriptionId());
        } catch (CloudException e) {
            log.error("Exception creating Azure client", e);
            throw e;
        }
    }

    private int indexOfPublicIpNetworkInterface(List<NvaConfiguration> nvaConfigurations,
                                                String publicIpAddressId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(publicIpAddressId),
            "publicIpAddressId cannot be null or empty");
        for (int i = 0; i < nvaConfigurations.size(); i++) {
            if (publicIpAddressId.equals(nvaConfigurations.get(i).getPublicIpNetworkInterface())) {
                return i;
            }
        }

        return -1;
    }

    private int indexOfRouteNetworkInterface(List<NvaConfiguration> nvaConfigurations,
                                             String privateIpAddress) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(privateIpAddress),
            "privateIpAddress cannot be null or empty");

        for (int i = 0; i < nvaConfigurations.size(); i++) {
            NetworkInterface networkInterface = this.azureClient.getNetworkInterfaceById(
                nvaConfigurations.get(i).getRouteNetworkInterface()
            );

            if (privateIpAddress.equals(networkInterface.primaryPrivateIp())) {
                return i;
            }
        }

        return -1;
    }

    private int getCurrentNvaIndex() {
        // We need to find out the current setup of the NVAs
        // TODO - We probably need to implement some self healing things around this.

        int pipNetworkInterfaceIndex = -1;
        int routeNetworkInterfaceIndex = -1;
        // This is so if we are operating in either pip or route, but not both.
        int currentNvaIndex = -1;
        String publicIpAddressId = this.configuration.getPublicIpAddress();
        if ((this.configuration.getOperatingMode() == OperatingMode.PIP_AND_ROUTE) ||
            (this.configuration.getOperatingMode() == OperatingMode.ONLY_PIP)) {
            PublicIpAddress publicIpAddress = this.azureClient.getPublicIpAddressById(publicIpAddressId);
            if (publicIpAddress == null) {
                throw new IllegalArgumentException("Invalid PublicIpAddress: " + publicIpAddressId);
            }

            if (!publicIpAddress.hasAssignedNetworkInterface()) {
                throw new IllegalArgumentException(
                    "PublicIpAddress " + publicIpAddressId + " is not assigned to a NetworkInterface");
            }

            NicIpConfiguration nicIpConfiguration =
                publicIpAddress.getAssignedNetworkInterfaceIpConfiguration();

            NetworkInterface networkInterface = nicIpConfiguration.parent();
            log.debug("NetworkInterface: " + networkInterface.id() + " PublicIpAddress: " + publicIpAddressId);
            pipNetworkInterfaceIndex = indexOfPublicIpNetworkInterface(
                this.configuration.getNvaConfigurations(),
                networkInterface.id());
            if (pipNetworkInterfaceIndex == -1) {
                throw new IllegalArgumentException("NetworkInterface " + networkInterface.id() +
                    " was not found in the list of valid network interfaces");
            }

            currentNvaIndex = pipNetworkInterfaceIndex;
        }

        // TODO - We need to figure out how to validate with multiple RouteTables!!!
        // We'll do it the safe way for now, but it will be a little slow
        // We will loop through all of the RouteTables (yuck), pull out the ones
        // with nextHopIpAddresses, distinct() them, and then see if one matches.
        if ((this.configuration.getOperatingMode() == OperatingMode.PIP_AND_ROUTE) ||
            (this.configuration.getOperatingMode() == OperatingMode.ONLY_ROUTE)) {

            List<String> nextHopIpAddresses = this.configuration.getRouteTables()
                .stream()
                .flatMap(id -> this.azureClient.getRouteTableById(id)
                    .routes().values().stream()
                    .filter(r -> !Strings.isNullOrEmpty(r.nextHopIpAddress()))
                    .map(r -> r.nextHopIpAddress()))
                .distinct()
                .collect(Collectors.toList());
            for (String nextHopIpAddress : nextHopIpAddresses) {
                int temp = this.indexOfRouteNetworkInterface(
                    this.configuration.getNvaConfigurations(), nextHopIpAddress);
                if (temp > -1) {
                    routeNetworkInterfaceIndex = temp;
                    break;
                }
            }

            if (routeNetworkInterfaceIndex == -1) {
                throw new IllegalArgumentException(
                    "No NetworkInterfaces match configured route tables");
            }

            currentNvaIndex = routeNetworkInterfaceIndex;
        }

        if ((this.configuration.getOperatingMode() == OperatingMode.PIP_AND_ROUTE) &&
            (pipNetworkInterfaceIndex != routeNetworkInterfaceIndex)) {
            // The indexes need to match here.  Otherwise, the pip and route are pointing at different NVAs
            // and the currentNvaIndex is wrong.  Let's attempt to heal things!
            NvaConfiguration from = this.configuration.getNvaConfigurations().get(
                routeNetworkInterfaceIndex);
            NvaConfiguration to = this.configuration.getNvaConfigurations().get(
                pipNetworkInterfaceIndex);
            this.migrateRouteTables(from, to);
            //throw new IllegalArgumentException("Current PublicIpAddress and Route point to different NVAs");
        }

        return currentNvaIndex;
    }

    private <T> T getById(String id, Function<String, T> getById) {
        log.debug("Getting resource: " + id);
        T resource = getById.apply(id);
        if (resource == null) {
            throw new IllegalArgumentException("Error getting resource: " + id);
        }

        log.debug("Got resource: " + id);

        return resource;
    }

    private <T> void migrate(String fromId, String toId, Function<String, T> getById,
                          BiConsumer<T, T> migration) {
        Preconditions.checkNotNull(fromId, "fromId cannot be null");
        Preconditions.checkNotNull(toId, "toId cannot be null");
        Preconditions.checkNotNull(getById, "getById cannot be null");
        Preconditions.checkNotNull(migration, "migration cannot be null");
        T from = getById(fromId, getById);
        T to = getById(toId, getById);
        migration.accept(from, to);
    }

    private void migrateAzureResources() {
        NvaConfiguration current = this.nvaConfigurations.current();
        NvaConfiguration next = this.nvaConfigurations.peek();

        if ((this.configuration.getOperatingMode() == OperatingMode.PIP_AND_ROUTE) ||
            (this.configuration.getOperatingMode() == OperatingMode.ONLY_PIP)) {
            migratePublicIpAddress(current, next);
        }

        if ((this.configuration.getOperatingMode() == OperatingMode.PIP_AND_ROUTE) ||
            (this.configuration.getOperatingMode() == OperatingMode.ONLY_ROUTE)) {
            migrateRouteTables(current, next);
        }

        this.nvaConfigurations.next();
    }

    private void migrateRouteTables(NvaConfiguration current,
                                    NvaConfiguration next) {
        migrate(current.getRouteNetworkInterface(),
            next.getRouteNetworkInterface(),
            this.azureClient.networkInterfaces()::getById,
            (from, to) -> {
                // Update the route tables
                for (String id : this.configuration.getRouteTables()) {
                    log.debug("Updating route table" + id +
                        " to " + to.primaryPrivateIp());
                    RouteTable routeTable = this.azureClient.getRouteTableById(
                        id);

                    List<String> routeNames = routeTable.routes().entrySet().stream()
                        .filter(e -> from.primaryPrivateIp().equals(
                            e.getValue().nextHopIpAddress()))
                        .map(e -> e.getKey())
                        .collect(Collectors.toList());
                    RouteTable.Update update = null;
                    for (String routeName : routeNames) {
                        update = routeTable.update()
                            .updateRoute(routeName)
                            .withNextHopToVirtualAppliance(to.primaryPrivateIp())
                            .parent();
                    }

                    if (update != null) {
                        update.apply();
                    }

                    log.debug("Updated route table" + id + " to " + to.primaryPrivateIp());
                }
            });
    }

    private void migratePublicIpAddress(NvaConfiguration current,
                                        NvaConfiguration next) {
        migrate(current.getPublicIpNetworkInterface(),
            next.getPublicIpNetworkInterface(),
            this.azureClient.networkInterfaces()::getById,
            (from, to) -> {
                PublicIpAddress publicIpAddress = this.azureClient.getPublicIpAddressById(
                    this.configuration.getPublicIpAddress());
                log.debug("Removing public ip address from network interface " + from.id());
                // Swap the pip
                from.update()
                    .withoutPrimaryPublicIpAddress()
                    .apply();
                log.debug("Public ip address removed from network interface " + from.id());

                log.debug("Adding public ip address to network interface " + to.id());
                to.update()
                    .withExistingPrimaryPublicIpAddress(publicIpAddress)
                    .apply();
                log.debug("Added public ip address to network interface " + to.id());
            });
    }

    @Override
    public void init(MonitorConfiguration configuration) throws Exception {
        Preconditions.checkNotNull(configuration, "config cannot be null");
        this.configuration = AzureProbeMonitorConfiguration.create(configuration);
        failures = 0;
        initializeAzure();
        this.configuration.validate(this.azureClient);
        int currentNvaIndex = this.getCurrentNvaIndex();
        this.nvaConfigurations = com.microsoft.azure.practices.nvadaemon.collect.Iterators.currentPeekingIterator(
            Iterators.peekingIterator(Iterators.cycle(this.configuration.getNvaConfigurations())));
        // This needs to be one greater than the current index, since the iterator is at the beginning.
        Iterators.advance(this.nvaConfigurations, currentNvaIndex + 1);
    }

    @Override
    public boolean probe() {
        try {
            NvaConfiguration current = this.nvaConfigurations.current();
            try (SocketChannel channel = SocketChannel.open()) {
                channel.connect(current.getProbeSocketAddress());
            }

            // If this works, we want to reset any previous failures.
            failures = 0;
        } catch (IOException e) {
            log.info("probe() threw an exception", e);
            failures++;
        }

        return failures < this.configuration.getNumberOfFailuresThreshold();
    }

    @Override
    public void execute() {
        log.info("Probe failure.  Executing failure action.");
        migrateAzureResources();
        failures = 0;
    }

    @Override
    public int getTime() {
        return this.configuration.getProbePollingInterval();
    }

    @Override
    public TimeUnit getUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    public void close() throws Exception {
        if (this.azureClient != null) {
            this.azureClient.close();
        }
    }
}
