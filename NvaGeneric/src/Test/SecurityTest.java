package Test;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.Before; 
import org.junit.After;
import org.apache.commons.codec.digest.*;
import org.apache.commons.lang.*;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.PublicKey;



public class SecurityTest {
	private String thumbprint = "9278849bf6dcb589b9dbdf77f216c6ee83de46d4";

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

}
