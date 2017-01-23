package com.microsoft.azure.practices.nvadaemon.credentials;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.GeneralSecurityException;

public class AzureClientIdCertificateCredentialFactoryImplTest {

    String clientId = "client-id";
    String keystorePath = "keystore-path";
    String keystorePassword = "keystore-password";
    String certificatePassword = "certificate-password";

    @Test
    void testNullClientId() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureClientIdCertificateCredentialFactoryImpl(null,
                keystorePath, keystorePassword, certificatePassword));
    }

    @Test
    void testEmptyClientId() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureClientIdCertificateCredentialFactoryImpl("",
                keystorePath, keystorePassword, certificatePassword));
    }

    @Test
    void testNullKeyStorePath() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureClientIdCertificateCredentialFactoryImpl(clientId,
                null, keystorePassword, certificatePassword));
    }

    @Test
    void testEmptyKeyStorePath() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureClientIdCertificateCredentialFactoryImpl(clientId,
                "", keystorePassword, certificatePassword));
    }

    @Test
    void testNullKeyStorePassword() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureClientIdCertificateCredentialFactoryImpl(clientId,
                keystorePath, null, certificatePassword));
    }

    @Test
    void testEmptyKeyStorePassword() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureClientIdCertificateCredentialFactoryImpl(clientId,
                keystorePath, "", certificatePassword));
    }

    @Test
    void testNullCertificatePassword() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureClientIdCertificateCredentialFactoryImpl(clientId,
                keystorePath, keystorePassword, null));
    }

    @Test
    void testEmptyCertificatePassword() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new AzureClientIdCertificateCredentialFactoryImpl(clientId,
                keystorePath, keystorePassword, ""));
    }

    @Test
    void testValidParameters() throws NoSuchFieldException {
        Assertions.assertAll(() -> new AzureClientIdCertificateCredentialFactoryImpl(clientId,
            keystorePath, keystorePassword, certificatePassword));
    }

    @Test
    void testInvalidFilePath() {
        AzureClientIdCertificateCredentialFactoryImpl factory =
            new AzureClientIdCertificateCredentialFactoryImpl(clientId,
            keystorePath, keystorePassword, certificatePassword);
        Assertions.assertThrows(GeneralSecurityException.class,
            () -> factory.create("resource"));
    }
}
