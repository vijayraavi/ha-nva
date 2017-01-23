package com.microsoft.azure.practices.nvadaemon;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.RestClient;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.network.RouteTable;
import com.microsoft.azure.management.resources.GenericResources;
import com.microsoft.azure.management.resources.fluentcore.arm.collection.SupportsGettingById;
import com.microsoft.azure.practices.nvadaemon.credentials.AzureClientIdCertificateCredentialFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AzureClientTest {
    ApplicationTokenCredentials tokenCredentials =
        new ApplicationTokenCredentials("clientId", "domain", "secret", AzureEnvironment.AZURE);
    String subscriptionId = "12345";

    @Test
    void testNullTokenCredentials() {
        Assertions.assertThrows(NullPointerException.class,
            () -> AzureClient.create(null, subscriptionId));
    }

    @Test
    void testNullSubscriptionId() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> AzureClient.create(tokenCredentials, null));
    }

    @Test
    void testEmptySubscriptionId() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> AzureClient.create(tokenCredentials, ""));
    }

    @Test
    void testSubscriptionId() {
        AzureClient azureClient = AzureClient.create(tokenCredentials, subscriptionId);
        String actual = azureClient.subscriptionId();
        Assertions.assertEquals(subscriptionId, actual);
    }

    @Test
    void testClose() {
        AzureClient azureClient = AzureClient.create(tokenCredentials, subscriptionId);
        Assertions.assertAll(() -> azureClient.close());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetByIdNullId() {
        SupportsGettingById<RouteTable> resources =
            (SupportsGettingById<RouteTable>)mock(SupportsGettingById.class);
        AzureClient azureClient = AzureClient.create(tokenCredentials, subscriptionId);
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> azureClient.getById(null, resources));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetByIdEmptyId() {
        SupportsGettingById<RouteTable> resources =
            (SupportsGettingById<RouteTable>)mock(SupportsGettingById.class);
        AzureClient azureClient = AzureClient.create(tokenCredentials, subscriptionId);
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> azureClient.getById("", resources));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetByIdNullResources() {
        AzureClient azureClient = AzureClient.create(tokenCredentials, subscriptionId);
        Assertions.assertThrows(NullPointerException.class,
            () -> azureClient.getById("12345", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetByIdResourceNotFound() {
        SupportsGettingById<RouteTable> resources =
            (SupportsGettingById<RouteTable>)mock(SupportsGettingById.class);
        when(resources.getById(anyString()))
            .thenReturn(null);
        AzureClient azureClient = AzureClient.create(tokenCredentials, subscriptionId);
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> azureClient.getById("12345", resources));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetByIdResourceFound() {
        SupportsGettingById<RouteTable> resources =
            (SupportsGettingById<RouteTable>)mock(SupportsGettingById.class);
        RouteTable mockRouteTable = mock(RouteTable.class);
        when(resources.getById(anyString()))
            .thenReturn(mockRouteTable);
        AzureClient azureClient = AzureClient.create(tokenCredentials, subscriptionId);
        RouteTable routeTable = azureClient.getById("12345", resources);
        Assertions.assertEquals(mockRouteTable, routeTable);
    }

//    @Test
//    void testAzureImpl() throws NoSuchMethodException, InstantiationException,
//        IllegalAccessException, InvocationTargetException {
//        GenericResources genericResources = mock(GenericResources.class);
//        when(genericResources.getById(anyString()))
//            .thenReturn(null);
//        Azure azure = mock(Azure.class);
//        when(azure.genericResources())
//            .thenReturn(genericResources);
//        RestClient restClient = mock(RestClient.class);
//        Constructor<AzureClient.AzureClientImpl> constructor =
//            AzureClient.AzureClientImpl.class.getDeclaredConstructor(Azure.class,
//                RestClient.class);
//        constructor.setAccessible(true);
//        AzureClient.AzureClientImpl azureClient = constructor.newInstance(azure, restClient);
//        Assertions.assertFalse(azureClient.checkExistenceById("test"));
//    }
}
