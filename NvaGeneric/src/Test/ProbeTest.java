package Test;

import org.junit.*;

import java.net.HttpURLConnection;
import java.net.*;
import java.net.URL;


import static org.junit.Assert.*;
import org.junit.Before; 
import org.junit.After;
import java.net.Socket;


import org.junit.Test;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.InterruptedException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.PrintWriter;

//import org.icmp4j.IcmpPingUtil;
//import org.icmp4j.IcmpPingRequest;
//import org.icmp4j.IcmpPingResponse;

public class ProbeTest {
	private String hostname;
	private int port;
	private Socket socket;
	
	
	
	 @Before public void initialize() {
	       hostname="23.96.235.104";
	       port=443;
	    }
	 
	 @After public void tearDown() throws Exception {
		 if(socket!=null)
	      socket.close();
	    }

    
	@Test
	public void canOpenSocketTest() throws Exception{

		socket = new Socket(hostname, port);
		assertThat(socket, instanceOf(Socket.class));
		assertTrue(socket.isConnected());
	}
	
	@Test
	public void canGetAckSocketTest() throws Exception{

		socket = new Socket(hostname, port);
		assertTrue(socket.isConnected());
		BufferedReader reader = new BufferedReader
				  (new InputStreamReader(socket.getInputStream()));
		PrintWriter writer  = new PrintWriter(socket.getOutputStream(),true);
      	writer.println("data");
		String data = reader.readLine(); 
		assertEquals(data,"ok");
		writer.close(); 
	}
	
	@Test
	public void canGetAckforHttpTest() throws Exception{
		
		  HttpURLConnection connection = (HttpURLConnection) 
				  new URL("http://23.96.235.104:8080").openConnection();
	        connection.setConnectTimeout(5000);
	        connection.setReadTimeout(5000);
	        connection.setRequestMethod("HEAD");
	        int responseCode = connection.getResponseCode();
	        assertEquals(connection.getResponseCode(),200);	
	
	}			
			
	
	

}
