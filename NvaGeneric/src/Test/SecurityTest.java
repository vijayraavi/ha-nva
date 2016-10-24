package Test;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.*;
import org.junit.Before; 
import org.junit.After;
import org.apache.commons.codec.digest.*;
import org.apache.commons.lang.*;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.PublicKey;
import java.security.PrivateKey;
import com.microsoft.rest.credentials.TokenCredentials;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.aad.adal4j.ClientCredential;
import com.microsoft.aad.adal4j.AsymmetricKeyCredential;
import com.microsoft.azure.Azure;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.credentials.UserTokenCredentials;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.network.PublicIpAddress;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.ResourceGroups;
import com.microsoft.rest.credentials.ServiceClientCredentials;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.security.cert.X509Certificate;



public class SecurityTest {
	private String thumbprint = "9278849bf6dcb589b9dbdf77f216c6ee83de46d4";
	private char[] passwd;
	private char[] passwdkey; 
	private String clientId;
	private String tenantId;	
	
	
	
	 @Before public void initialize() {
		 // this is the passworkd for the keystore
		passwd = "changeit".toCharArray();
		// this is the password for the certificate 
		// it is required becasue the api uses to sign the asserttion
	    passwdkey = "Pag$1Lab".toCharArray();
	 	String clientId = "10686b6e-b797-4f4f-9e5d-56b6aaa3b377";
		String tenantId = "72f988bf-86f1-41af-91ab-2d7cd011db47";	
	    }
	 
	 @After public void tearDown() throws Exception {
	     
	    }
	
	
	@Test
	public void canGetCertificateFromStore() throws Exception {
		
		File file = new File("e:\\certificates\\publicKey.store");
        FileInputStream fileinput = new FileInputStream(file);
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        String password = "changeit";
        keystore.load(fileinput, password.toCharArray());
        Certificate certificate = keystore.getCertificate("mykey");
        String certThumbprint =  DigestUtils.sha1Hex(certificate.getEncoded());
        assertTrue(certThumbprint.matches(thumbprint));
        fileinput.close();
	}
	
	@Test
	public void canGetX509FromStore() throws Exception {
		
		
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		File file = new File("e:\\certificates\\privatekeystore.jks");
	    FileInputStream fileinput = new FileInputStream(file);
	    ks.load(new FileInputStream("e:\\certificates\\privatekeystore.jks"), passwd);
	    String alias = ks.aliases().nextElement();
        KeyStore.PrivateKeyEntry keyEnt = (KeyStore.PrivateKeyEntry)
        		 ks.getEntry(alias, new KeyStore.PasswordProtection(passwdkey)); 
	    X509Certificate cert = (X509Certificate)keyEnt.getCertificate();
	    String certThumbprint =  DigestUtils.sha1Hex(cert.getEncoded());
	    assertTrue(certThumbprint.matches(thumbprint));
	    fileinput.close();
	}
	
//

	@Test
	public void canAzureAuthCertAssertion() throws Exception {
		
		String clientId = "10686b6e-b797-4f4f-9e5d-56b6aaa3b377";
		String tenantId = "72f988bf-86f1-41af-91ab-2d7cd011db47";			
		char[] passwd = "changeit".toCharArray();
		char[] passwdkey = "Pag$1Lab".toCharArray();

		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		File file = new File("e:\\certificates\\privatekeystore.jks");
	    FileInputStream fileinput = new FileInputStream(file);
	    ks.load(new FileInputStream("e:\\certificates\\privatekeystore.jks"), passwd);
	    String alias = ks.aliases().nextElement();
        KeyStore.PrivateKeyEntry keyEnt = (KeyStore.PrivateKeyEntry)
        		 ks.getEntry(alias, new KeyStore.PasswordProtection(passwdkey)); 

	    X509Certificate cert = (X509Certificate)keyEnt.getCertificate();
	  
	    PrivateKey key = (PrivateKey) ks.getKey(alias, passwdkey);		
	     
		String url = "https://login.microsoftonline.com/" + tenantId + "/oauth2/authorize";
		String urlmgm = "https://management.azure.com/";
		ExecutorService service = Executors.newFixedThreadPool(1);
		AuthenticationContext authContext = new AuthenticationContext(url,
	                                            false,
	                                            service);
		
		AsymmetricKeyCredential credential = AsymmetricKeyCredential.create(clientId,key,cert );
		
		Future<AuthenticationResult>  future = authContext.acquireToken(
                 "https://management.azure.com/",
                 credential,
                 null);
		 
        AuthenticationResult authResult = future.get(); 
        System.out.println(authResult.getAccessToken());
		  
        ServiceClientCredentials credentials = new TokenCredentials(null,authResult.getAccessToken());             
        Azure azure = Azure
                  .configure()
                  .authenticate(credentials)
                  .withDefaultSubscription();	
        
         assertNotNull(authResult.getAccessToken());
         assertNotNull(azure);
         
         
         
	}

}
