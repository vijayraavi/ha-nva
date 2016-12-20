package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.network.*;
import com.microsoft.azure.practices.nvadaemon.collect.CurrentPeekingIterator;
import com.microsoft.azure.practices.nvadaemon.config.*;
import com.microsoft.azure.practices.nvadaemon.credentials.AsymmetricKeyCredentialFactory;
import com.microsoft.azure.practices.nvadaemon.credentials.AzureClientIdCertificateCredentialFactoryImpl;
import com.microsoft.azure.practices.nvadaemon.credentials.CertificateCredentials;
import com.microsoft.azure.practices.nvadaemon.monitor.ScheduledMonitor;
import com.microsoft.azure.practices.nvadaemon.config.AzureConfiguration.ServicePrincipal;
import com.microsoft.azure.practices.nvadaemon.config.AzureConfiguration.ServicePrincipal.AuthenticationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AzureProbeMonitor implements ScheduledMonitor {

    private final Logger log = LoggerFactory.getLogger(AzureProbeMonitor.class);
    private int failures;
    private AzureClient azureClient;
    private AzureProbeMonitorConfiguration configuration;
    private CurrentPeekingIterator<NvaConfiguration> nvaConfigurations;

    public AzureProbeMonitor(MonitorConfiguration monitorConfiguration)
        throws ConfigurationException {
        this.configuration = AzureProbeMonitorConfiguration.create(
            Preconditions.checkNotNull(monitorConfiguration, "monitorConfiguration cannot be null"));
        this.failures = 0;
        initializeAzure();
        this.configuration.validate(this.azureClient);
    }

    private void initializeAzure() throws CloudException {
        try {
            AzureTokenCredentials credentials;
            AzureConfiguration azureConfiguration = this.configuration.getAzureConfiguration();
            ServicePrincipal servicePrincipal =
                azureConfiguration.getServicePrincipal();

            if (servicePrincipal.getAuthenticationMode() == AuthenticationMode.PASSWORD) {
                credentials = new ApplicationTokenCredentials(
                    servicePrincipal.getClientId(), servicePrincipal.getTenantId(),
                    servicePrincipal.getClientSecret(), AzureEnvironment.AZURE);
            } else if (servicePrincipal.getAuthenticationMode() == AuthenticationMode.CERTIFICATE) {
                AsymmetricKeyCredentialFactory factory =
                    new AzureClientIdCertificateCredentialFactoryImpl(
                        servicePrincipal.getClientId(),
                        servicePrincipal.getClientCertificate().getKeyStorePath(),
                        servicePrincipal.getClientCertificate().getKeyStorePassword(),
                        servicePrincipal.getClientCertificate().getCertificatePassword());

                credentials = new CertificateCredentials(
                    servicePrincipal.getTenantId(), AzureEnvironment.AZURE, factory);
            } else {
                throw new IllegalArgumentException("Unsupported AuthenticationMode: " +
                servicePrincipal.getAuthenticationMode());
            }

            this.azureClient = AzureClient.create(credentials,
                azureConfiguration.getSubscriptionId());
        } catch (CloudException e) {
            this.log.error("Exception creating Azure client", e);
            throw e;
        }
    }

    private int getCurrentNvaIndex() {
        // We need to find out the current setup of the NVAs
        // We are going to change this from the original version.  In order to save some cycles,
        // the first non-negative index we find, we will assume is the current NVA.  We will then
        // verify the rest of the configuration.  If something doesn't match (with the algorithm
        // being heavily weighted to RouteTables, since switching routes is faster), we will
        // change the RouteTables to match the current index.

        Map<String, PublicIpAddress> publicIpAddresses = this.configuration.getPublicIpAddresses().stream()
            .collect(Collectors.toMap(r -> r.getName(), r -> this.azureClient.getPublicIpAddressById(r.getId())));
        List<RouteTable> routeTables = this.configuration.getRouteTables().stream()
            .map(id -> this.azureClient.getRouteTableById(id))
            .collect(Collectors.toList());
        for (int i = 0; i < this.configuration.getNvaConfigurations().size(); i++) {
            NvaConfiguration nvaConfiguration = this.configuration.getNvaConfigurations().get(i);
            for (NamedResourceId networkInterface : nvaConfiguration.getNetworkInterfaces()) {
                PublicIpAddress publicIpAddress = publicIpAddresses.get(networkInterface.getName());
                // If publicIpAddress is null, this network interface is not assigned to a
                // public ip address.
                if ((publicIpAddress != null) &&
                    (publicIpAddress.hasAssignedNetworkInterface()) &&
                    (publicIpAddress.getAssignedNetworkInterfaceIpConfiguration().parent()
                        .id().equals(networkInterface.getId()))) {
                    return i;
                }

            }

            Set<String> privateIpAddresses = nvaConfiguration.getNetworkInterfaces().stream()
                .map(r -> this.azureClient.getNetworkInterfaceById(r.getId()).primaryPrivateIp())
                .collect(Collectors.toSet());
            Set<String> nextHopIpAddresses = routeTables.stream()
                .flatMap(rt -> rt.routes().values().stream()
                    .filter(r -> r.nextHopType().equals(RouteNextHopType.VIRTUAL_APPLIANCE))
                    .map(r -> r.nextHopIpAddress()))
                .distinct()
                .collect(Collectors.toSet());
            privateIpAddresses.retainAll(nextHopIpAddresses);
            if (!privateIpAddresses.isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private void migrateAzureResources(NvaConfiguration nvaConfiguration) {
        Preconditions.checkNotNull(nvaConfiguration, "nvaConfiguration cannot be null");
        this.migratePublicIpAddress(nvaConfiguration);
        this.migrateRouteTables(nvaConfiguration);
    }

    private void migrateRouteTables(NvaConfiguration next) {
        // We are going to migrate all routes that start with any of the other private ip
        // addresses.
        Map<String, String> toMap = next.getNetworkInterfaces().stream()
            .collect(Collectors.toMap(r -> r.getName(),
                r -> this.azureClient.getNetworkInterfaceById(r.getId()).primaryPrivateIp()));
        Map<String, List<String>> fromMap = this.configuration.getNvaConfigurations().stream()
            .filter(c -> !c.equals(next))
            .flatMap(c -> c.getNetworkInterfaces().stream())
            .collect(Collectors.groupingBy(r -> r.getName(),
                Collectors.mapping(
                    r -> this.azureClient.getNetworkInterfaceById(r.getId()).primaryPrivateIp(),
                    Collectors.toList())));
        List<RouteTable> routeTables = this.configuration.getRouteTables().stream()
            .map(id -> this.azureClient.getRouteTableById(id))
            .collect(Collectors.toList());
        for (RouteTable routeTable : routeTables) {
            RouteTable.Update update = null;
            for (String nicGroup : fromMap.keySet()) {
                String toIpAddress = toMap.get(nicGroup);
                List<String> routeNames = routeTable.routes().entrySet().stream()
                    .filter(e -> fromMap.get(nicGroup).contains(e.getValue().nextHopIpAddress()))
                    .map(e -> e.getKey())
                    .collect(Collectors.toList());
                for (String routeName : routeNames) {
                    update = routeTable.update()
                        .updateRoute(routeName)
                        .withNextHopToVirtualAppliance(toIpAddress)
                        .parent();
                }
            }

            if (update != null) {
                this.log.debug("Updating route table" + routeTable.id());
                update.apply();
                this.log.debug("Updated route table" + routeTable.id());
            }
        }
    }

    private void migratePublicIpAddress(NvaConfiguration next) {
        Map<String, NetworkInterface> toMap = next.getNetworkInterfaces().stream()
            .collect(Collectors.toMap(r -> r.getName(),
                r -> this.azureClient.getNetworkInterfaceById(r.getId())));
        Map<String, PublicIpAddress> publicIpAddresses =
            this.configuration.getPublicIpAddresses().stream()
            .collect(Collectors.toMap(r -> r.getName(),
                r -> this.azureClient.getPublicIpAddressById(r.getId())));
        for (Map.Entry<String, PublicIpAddress> entry : publicIpAddresses.entrySet()) {
            NetworkInterface toNetworkInterface = toMap.get(entry.getKey());
            if (toNetworkInterface != null) {
                PublicIpAddress publicIpAddress = entry.getValue();
                NetworkInterface publicIpAddressNetworkInterface = null;
                if (publicIpAddress.hasAssignedNetworkInterface()) {
                    publicIpAddressNetworkInterface =
                        publicIpAddress.getAssignedNetworkInterfaceIpConfiguration()
                        .parent();
                }

                boolean migratePip = false;
                if (publicIpAddressNetworkInterface == null) {
                    migratePip = true;
                } else if (!publicIpAddressNetworkInterface.id().equals(
                        toNetworkInterface.id())) {
                    migratePip = true;
                    this.log.debug("Removing public ip address from network interface " +
                        publicIpAddressNetworkInterface.id());

                    publicIpAddressNetworkInterface.update()
                        .withoutPrimaryPublicIpAddress()
                        .apply();
                    this.log.debug("Public ip address removed from network interface " +
                        publicIpAddressNetworkInterface.id());

                    this.log.debug("Adding public ip address to network interface " +
                        toNetworkInterface.id());
                    toNetworkInterface.update()
                        .withExistingPrimaryPublicIpAddress(publicIpAddress)
                        .apply();
                    this.log.debug("Added public ip address to network interface " +
                        toNetworkInterface.id());
                }

                if (migratePip) {
                    this.log.debug("Adding public ip address to network interface " +
                        toNetworkInterface.id());
                    toNetworkInterface.update()
                        .withExistingPrimaryPublicIpAddress(publicIpAddress)
                        .apply();
                    this.log.debug("Added public ip address to network interface " +
                        toNetworkInterface.id());
                }
            }
        }
    }

    private boolean isNvaValid(NvaConfiguration nvaConfiguration) {
        Preconditions.checkNotNull(nvaConfiguration, "nvaConfiguration cannot be null");
        Map<String, String> networkInterfaces =
            nvaConfiguration.getNetworkInterfaces().stream()
            .collect(Collectors.toMap(c -> c.getName(),
                c -> c.getId()));
        Map<String, PublicIpAddress> publicIpAddresses = this.configuration.getPublicIpAddresses().stream()
            .collect(Collectors.toMap(r -> r.getName(),
                r -> this.azureClient.getPublicIpAddressById(r.getId())));

        if (publicIpAddresses.values().stream()
            .filter(p -> !p.hasAssignedNetworkInterface())
            .count() > 0) {
            // One of the PublicIpAddress resources is not attached.  A repair is needed.
            return false;
        }

        for (Map.Entry<String, PublicIpAddress> publicIpAddressEntry : publicIpAddresses.entrySet()) {
            String networkInterfaceId = networkInterfaces.get(publicIpAddressEntry.getKey());
            if (!publicIpAddressEntry.getValue()
                .getAssignedNetworkInterfaceIpConfiguration()
                .parent().id().equals(networkInterfaceId)) {
                // One of the PublicIpAddresses assigned to a different NVA.  A repair is needed.
                return false;
            }
        }

        Set<String> privateIpAddresses = networkInterfaces.values().stream()
            .map(id -> this.azureClient.getNetworkInterfaceById(id).primaryPrivateIp())
            .collect(Collectors.toSet());
        Set<String> nextHopIpAddresses = this.configuration.getRouteTables().stream()
            .map(id -> this.azureClient.getRouteTableById(id))
            .flatMap(rt -> rt.routes().values().stream()
                .filter(r -> r.nextHopType().equals(RouteNextHopType.VIRTUAL_APPLIANCE))
                .map(r -> r.nextHopIpAddress()))
            .distinct()
            .collect(Collectors.toSet());
        nextHopIpAddresses.removeAll(privateIpAddresses);
        if (!nextHopIpAddresses.isEmpty()) {
            // One of the routes is pointing to a different NVA.  A repair is needed.
            return false;
        }

        return true;
    }

    @Override
    //public void init(MonitorConfiguration configuration) throws Exception {
    public void init() throws Exception {
//        Preconditions.checkNotNull(configuration, "config cannot be null");
//        this.configuration = AzureProbeMonitorConfiguration.create(configuration);
        this.failures = 0;
//        initializeAzure();
//        this.configuration.validate(this.azureClient);
        int currentNvaIndex = this.getCurrentNvaIndex();
        if (currentNvaIndex == -1) {
            throw new UnsupportedOperationException("Active NVA was not found");
        }

        this.nvaConfigurations = com.microsoft.azure.practices.nvadaemon.collect.Iterators.currentPeekingIterator(
            Iterators.peekingIterator(Iterators.cycle(this.configuration.getNvaConfigurations())));
        // This needs to be one greater than the current index, since the iterator is at the beginning.
        Iterators.advance(this.nvaConfigurations, currentNvaIndex + 1);
        NvaConfiguration current = this.nvaConfigurations.current();
        if (!this.isNvaValid(current)) {
            this.migrateAzureResources(current);
        }
    }

    @Override
    public boolean probe() {
        try {
            NvaConfiguration current = this.nvaConfigurations.current();
            try (SocketChannel channel = SocketChannel.open()) {
                channel.socket().connect(current.getProbeSocketAddress(),
                    this.configuration.getProbeConnectTimeout());
            }

            // If this works, we want to reset any previous failures.
            this.failures = 0;
        } catch (IOException e) {
            log.info("probe() threw an exception", e);
            this.failures++;
        }

        return this.failures < this.configuration.getNumberOfFailuresThreshold();
    }

    @Override
    public void execute() {
        log.info("Probe failure.  Executing failure action.");
        this.migrateAzureResources(this.nvaConfigurations.next());
        this.failures = 0;
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
            this.azureClient = null;
        }
    }
}
