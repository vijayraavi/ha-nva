package com.microsoft.azure.practices;
import java.net.Socket;
import java.net.ServerSocket;

public class ClientWorker implements Runnable  {
	private Socket client;
	
	 ClientWorker(Socket client) {
		    this.client = client;
		  }
	 
	  public void run(){
		  
	  }
		  


}
