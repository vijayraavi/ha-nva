package com.microsoft.azure.practices;

import java.net.Socket;
import java.net.ServerSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.InterruptedException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.PrintStream;
import java.io.PrintWriter;


public class NvaProbeServer implements ProbeServer, Runnable {
	
	 private final ServerSocket serverSocket;
	 private PrintWriter writer;
	 private  BufferedReader reader;
	 	 
	 public NvaProbeServer(int port) throws IOException{
		 serverSocket = new ServerSocket(port);	 
	 }
	 	 
	 public void run(){
		 
		 //while probeThread is not interrupted 
		 // sever keeps accepting socket connections
		 while(!Thread.currentThread().isInterrupted()) 
		 {
			 try{	
				 System.out.println("listening on socket"); 
				 Socket clientSocket = serverSocket.accept();
				 System.out.println("communication started");
				 reader = new BufferedReader
						  (new InputStreamReader(clientSocket.getInputStream()));			 
				 String data = reader.readLine(); 
			    			     		  	
		         if (data!=null){
		        	 System.out.println(data); 
		        	 writer  = new PrintWriter(clientSocket.getOutputStream(),true);
		        	 writer.println("ok");
		        	 writer.close();		        	
		         }		         
			 }			 
			  catch (Exception exception){
				  System.err.println(exception.getStackTrace());	
			  }	 
		 }	  
	  }
	 
	@Override
	public void close() throws IOException {
		System.out.println("Closing Socket");
		serverSocket.close();
	}

}
