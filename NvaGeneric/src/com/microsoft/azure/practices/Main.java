package com.microsoft.azure.practices;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;




public class Main {
	
	 public static void main(final String[] args) throws Exception {
				 
		 try
		 {	 
			   DaemonContext context = new DaemonContext() {
	               public DaemonController getController() {
	                   return null;
	               }	
	               public String[] getArguments() {
	                   return args;
	               }
	           };	           
		       NvaProbeDaemon daemon = new NvaProbeDaemon();
	           daemon.init(context);
	           daemon.start();
	           System.out.println("Press enter to quit...");
	           new BufferedReader(new InputStreamReader(System.in)).readLine();
	           daemon.stop();
	           daemon.destroy();
			 
		 }
		 catch (Exception e)
	     {
	           System.err.println("ERROR!");
	           System.err.println(e.getStackTrace());	           
	     }
	 }

}
