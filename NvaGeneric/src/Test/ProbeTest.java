package Test;


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


public class ProbeTest {
	private String hostname;
	private int port;
	private Socket socket;
	
	
	
	 @Before public void initialize() {
	       hostname="localhost";
	       port=9010;
	    }
	 
	 @After public void tearDown() throws Exception {
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
      	writer.println("test2");
		String data = reader.readLine(); 
		assertEquals(data,"ok");
		writer.close(); 
	}

}
