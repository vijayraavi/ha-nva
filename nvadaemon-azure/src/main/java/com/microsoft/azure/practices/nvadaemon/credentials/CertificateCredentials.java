package com.microsoft.azure.practices.nvadaemon.credentials;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentialsInterceptor;
import com.microsoft.rest.credentials.TokenCredentials;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CertificateCredentials extends TokenCredentials implements AzureTokenCredentials {

    private Map<String, AuthenticationResult> tokens;
    private String domain;
    private AzureEnvironment environment;
    private AsymmetricKeyCredentialFactory credentialFactory;

    public CertificateCredentials(String domain, AzureEnvironment environment,
                                  AsymmetricKeyCredentialFactory credentialFactory) {
        super(null, null);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(domain),
            "domain cannot be null or empty");
        this.domain = domain;
        this.credentialFactory = Preconditions.checkNotNull(credentialFactory,
            "credentialFactory cannot be null");
        this.environment = Preconditions.checkNotNull(environment,
            "environment cannot be null or empty");
        this.tokens = new HashMap<>();
    }

    @Override
    public String getDomain() {
        return this.domain;
    }

    @Override
    public AzureEnvironment getEnvironment() {
        return this.environment;
    }

    @Override
    public String getToken(String resource) throws IOException {
        AuthenticationResult authenticationResult = this.tokens.get(resource);
        if(authenticationResult == null || authenticationResult.getExpiresOnDate().before(new Date())) {
            authenticationResult = this.acquireAccessToken(resource);
        }

        return authenticationResult.getAccessToken();
    }

    private AuthenticationResult acquireAccessToken(String resource) throws IOException {
        String authorityUrl = this.getEnvironment().getAuthenticationEndpoint() + this.getDomain();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AuthenticationContext context = new AuthenticationContext(authorityUrl,
            this.getEnvironment().isValidateAuthority(), executor);

        try {
            AuthenticationResult authenticationResult = context.acquireToken(
                resource, this.credentialFactory.create(resource), null).get();
            this.tokens.put(resource, authenticationResult);
            return authenticationResult;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        } finally {
            executor.shutdown();
        }
    }

    public void applyCredentialsFilter(OkHttpClient.Builder clientBuilder) {
        clientBuilder.interceptors().add(new AzureTokenCredentialsInterceptor(this));
    }
}
