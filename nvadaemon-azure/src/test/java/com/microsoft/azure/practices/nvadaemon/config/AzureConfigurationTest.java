package com.microsoft.azure.practices.nvadaemon.config;

import com.google.common.util.concurrent.Service;
import com.microsoft.azure.practices.nvadaemon.config.AzureConfiguration.ServicePrincipal;
import com.microsoft.azure.practices.nvadaemon.config.AzureConfiguration.ServicePrincipal.AuthenticationMode;
import com.microsoft.azure.practices.nvadaemon.config.AzureConfiguration.ServicePrincipal.ClientCertificate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AzureConfigurationTest {
    @Test
    void test_null_subscription_id() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureConfiguration(null, null));
    }

    @Test
    void test_empty_subscription_id() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureConfiguration("", null));
    }

    @Test
    void test_null_service_principal() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new AzureConfiguration("subscription-id", null));
    }

    @Test
    void test_subscription_id_and_service_principal() {
        String subscriptionId = "subscription-id";
        ServicePrincipal servicePrincipal = new ServicePrincipal("tenant-id",
            "client-id", "client-secret", null);
        AzureConfiguration azureConfiguration = new AzureConfiguration(subscriptionId,
            servicePrincipal);
        Assertions.assertEquals(subscriptionId, azureConfiguration.getSubscriptionId());
        Assertions.assertEquals(servicePrincipal, azureConfiguration.getServicePrincipal());
    }

    // ServicePrincipal tests
    @Test
    void test_null_tenant_id() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ServicePrincipal(null, null, null, null));
    }

    @Test
    void test_empty_tenant_id() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ServicePrincipal("", null, null, null));
    }

    @Test
    void test_null_client_id() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ServicePrincipal("tenant-id", null, null, null));
    }

    @Test
    void test_empty_client_id() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ServicePrincipal("tenant-id", "", null, null));
    }

    @Test
    void test_tenant_id_and_client_id_and_client_secret() {
        String tenantId = "tenant-id";
        String clientId = "client-id";
        String clientSecret = "client-secret";
        ServicePrincipal servicePrincipal = new ServicePrincipal(tenantId,
            clientId, clientSecret, null);
        Assertions.assertEquals(tenantId, servicePrincipal.getTenantId());
        Assertions.assertEquals(clientId, servicePrincipal.getClientId());
        Assertions.assertEquals(clientSecret, servicePrincipal.getClientSecret());
    }

    // Client certificate tests
    @Test
    void test_null_keystore_path() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ClientCertificate(null, null, null));
    }

    @Test
    void test_empty_keystore_path() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ClientCertificate("", null, null));
    }

    @Test
    void test_null_keystore_password() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ClientCertificate("keystore-path", null, null));
    }

    @Test
    void test_empty_keystore_password() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ClientCertificate("keystore-path", "", null));
    }

    @Test
    void test_null_certificate_password() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ClientCertificate("keystore-path", "keystore-password", null));
    }

    @Test
    void test_empty_certificate_password() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ClientCertificate("keystore-path", "keystore-password", ""));
    }

    @Test
    void test_client_certificate() {
        String keyStorePath = "keystore-path";
        String keyStorePassword = "keystore-password";
        String certificatePassword = "certificate-password";
        ClientCertificate clientCertificate = new ClientCertificate(keyStorePath,
            keyStorePassword, certificatePassword);
        Assertions.assertEquals(keyStorePath, clientCertificate.getKeyStorePath());
        Assertions.assertEquals(keyStorePassword, clientCertificate.getKeyStorePassword());
        Assertions.assertEquals(certificatePassword, clientCertificate.getCertificatePassword());
    }

    // AuthenticationMode tests
    @Test
    void test_authentication_mode_values() {
        AuthenticationMode[] values = AuthenticationMode.values();
        Assertions.assertEquals(2, values.length);
    }

    @Test
    void test_authentication_mode_value_of() {
        String passwordValue = "PASSWORD";
        String certificateValue = "CERTIFICATE";
        AuthenticationMode password = AuthenticationMode.valueOf(passwordValue);
        AuthenticationMode certificate = AuthenticationMode.valueOf(certificateValue);
        Assertions.assertEquals(password, AuthenticationMode.PASSWORD);
        Assertions.assertEquals(certificate, AuthenticationMode.CERTIFICATE);
    }

    @Test
    void test_null_client_secret_and_null_client_certificate() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ServicePrincipal("tenant-id", "client-id", null, null));
    }

    @Test
    void test_empty_client_secret_and_null_client_certificate() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ServicePrincipal("tenant-id", "client-id", "", null));
    }

    @Test
    void test_client_secret_and_null_client_certificate() {
        String clientSecret = "client-secret";
        ServicePrincipal servicePrincipal =
            new ServicePrincipal("tenant-id", "client-id", clientSecret, null);
        Assertions.assertEquals(AuthenticationMode.PASSWORD,
            servicePrincipal.getAuthenticationMode());
        Assertions.assertEquals(clientSecret,
            servicePrincipal.getClientSecret());
    }

    @Test
    void test_null_client_secret_and_client_certificate() {
        ClientCertificate clientCertificate =
            new ClientCertificate("keystore-path", "keystore-password", "certificate-password");
        ServicePrincipal servicePrincipal =
            new ServicePrincipal("tenant-id", "client-id", null, clientCertificate);
        Assertions.assertEquals(AuthenticationMode.CERTIFICATE,
            servicePrincipal.getAuthenticationMode());
        Assertions.assertEquals(clientCertificate,
            servicePrincipal.getClientCertificate());
    }

    @Test
    void test_empty_client_secret_and_client_certificate() {
        ClientCertificate clientCertificate =
            new ClientCertificate("keystore-path", "keystore-password", "certificate-password");
        ServicePrincipal servicePrincipal =
            new ServicePrincipal("tenant-id", "client-id", "", clientCertificate);
        Assertions.assertEquals(AuthenticationMode.CERTIFICATE,
            servicePrincipal.getAuthenticationMode());
        Assertions.assertEquals(clientCertificate,
            servicePrincipal.getClientCertificate());
    }

    @Test
    void test_client_secret_and_client_certificate() {
        ClientCertificate clientCertificate =
            new ClientCertificate("keystore-path", "keystore-password", "certificate-password");
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ServicePrincipal("tenant-id", "client-id", "client-secret", clientCertificate));
    }
}
