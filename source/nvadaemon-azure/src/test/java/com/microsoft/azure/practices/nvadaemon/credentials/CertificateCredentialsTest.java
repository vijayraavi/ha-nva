package com.microsoft.azure.practices.nvadaemon.credentials;

import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.azure.AzureEnvironment;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CertificateCredentialsTest {
    String domain = "domain";
    AzureEnvironment environment = AzureEnvironment.AZURE;

    @Test
    void testNullDomain() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new CertificateCredentials(null, environment,
            mock(AsymmetricKeyCredentialFactory.class)));
    }

    @Test
    void testEmptyDomain() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new CertificateCredentials("", environment,
                mock(AsymmetricKeyCredentialFactory.class)));
    }

    @Test
    void testNullEnvironment() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new CertificateCredentials(domain, null,
                mock(AsymmetricKeyCredentialFactory.class)));
    }

    @Test
    void testNullFactory() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new CertificateCredentials(domain, environment,
                null));
    }

    @Test
    void testDomain() {
        CertificateCredentials credentials = new CertificateCredentials(domain,
            environment, mock(AsymmetricKeyCredentialFactory.class));
        Assertions.assertEquals(domain, credentials.getDomain());
    }

    @Test
    void testEnvironment() {
        CertificateCredentials credentials = new CertificateCredentials(domain,
            environment, mock(AsymmetricKeyCredentialFactory.class));
        Assertions.assertEquals(environment, credentials.getEnvironment());
    }

    @Test
    void testValidToken() throws NoSuchFieldException, IllegalAccessException, IOException {
        String resource = "resource";
        String accessToken = "accessToken";
        Field tokensField = CertificateCredentials.class.getDeclaredField("tokens");
        tokensField.setAccessible(true);
        CertificateCredentials credentials = new CertificateCredentials(domain,
            environment, mock(AsymmetricKeyCredentialFactory.class));
        Map<String, AuthenticationResult> tokens =
            (Map<String, AuthenticationResult>)tokensField.get(credentials);
        AuthenticationResult authenticationResult = new AuthenticationResult(
            "accessTokenType", accessToken, "refreshToken", 1000, "idToken", null, false);
        tokens.put(resource, authenticationResult);
        String token = credentials.getToken(resource);
        Assertions.assertEquals(accessToken, token);
    }
}
