package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.aad.adal4j.*;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentialsInterceptor;
import com.microsoft.rest.credentials.TokenCredentials;
import okhttp3.OkHttpClient;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CertificateCredentials extends TokenCredentials implements AzureTokenCredentials {

    private static final String PATH_TO_KEYSTORE="azure.keyStore";
    private static final String KEYSTORE_PASSWORD="azure.keyStorePassword";
    private static final String CERTIFICATE_PASSWORD="azure.certificatePassword";
    private static final String KEYSTORE_ENTRY_ALIAS="azure.keyStoreEntryAlias";

    private Map<String, AuthenticationResult> tokens;
    private String clientId;
    private String domain;
    private AzureEnvironment environment;
    private Map<String, String> config;

    public CertificateCredentials(String clientId, String domain, AzureEnvironment environment,
                                  Map<String, String> config) {
        super(null, null);
        Preconditions.checkNotNull(config, "config cannot be null");
        this.clientId = clientId;
        this.domain = domain;
        this.environment = environment;
        this.tokens = new HashMap<>();
        this.config = config;
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

    private AsymmetricKeyCredential createAsymmetricKeyCredential() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream inputStream = new FileInputStream(this.config.get(PATH_TO_KEYSTORE))) {
            keyStore.load(inputStream, this.config.get(KEYSTORE_PASSWORD).toCharArray());
            // Assuming there is only one entry here.
            String alias = keyStore.aliases().nextElement();
            //PrivateKeyEntry privateKeyEntry = (PrivateKeyEntry)keyStore.getEntry(this.config.get(KEYSTORE_ENTRY_ALIAS),
            PrivateKeyEntry privateKeyEntry = (PrivateKeyEntry)keyStore.getEntry(alias,
                new PasswordProtection(this.config.get(CERTIFICATE_PASSWORD).toCharArray()));
            AsymmetricKeyCredential credential = AsymmetricKeyCredential.create(
                this.clientId, privateKeyEntry.getPrivateKey(), (X509Certificate) privateKeyEntry.getCertificate());
            return credential;
        } catch (IOException e) {
            throw new GeneralSecurityException("Could not load KeyStore file", e);
        } //catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | UnrecoverableEntryException e) {

        //}
    }
    private AuthenticationResult acquireAccessToken(String resource) throws IOException {
        String authorityUrl = this.getEnvironment().getAuthenticationEndpoint() + this.getDomain();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AuthenticationContext context = new AuthenticationContext(authorityUrl, this.getEnvironment().isValidateAuthority(), executor);

        try {
            AuthenticationResult authenticationResult = context.acquireToken(
                resource, createAsymmetricKeyCredential(), null).get();
            this.tokens.put(resource, authenticationResult);
            return authenticationResult;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        } finally {
            executor.shutdown();
        }
    }

//    private AuthenticationResult acquireAccessTokenFromRefreshToken(String resource) throws IOException {
//        String authorityUrl = this.getEnvironment().getAuthenticationEndpoint() + this.getDomain();
//        ExecutorService executor = Executors.newSingleThreadExecutor();
//        AuthenticationContext context = new AuthenticationContext(authorityUrl, this.getEnvironment().isValidateAuthority(), executor);
//
//        AuthenticationResult var6;
//        try {
//            AuthenticationResult e = (AuthenticationResult)context.acquireTokenByRefreshToken(((AuthenticationResult)this.tokens.get(resource)).getRefreshToken(), this.getClientId(), (ClientAssertion)null, (AuthenticationCallback)null).get();
//            this.tokens.put(resource, e);
//            var6 = e;
//        } catch (Exception var10) {
//            throw new IOException(var10.getMessage(), var10);
//        } finally {
//            executor.shutdown();
//        }
//
//        return var6;
//    }

    public void applyCredentialsFilter(OkHttpClient.Builder clientBuilder) {
        clientBuilder.interceptors().add(new AzureTokenCredentialsInterceptor(this));
    }
}
