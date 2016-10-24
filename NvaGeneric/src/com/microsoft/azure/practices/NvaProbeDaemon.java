package com.microsoft.azure.practices;

import java.io.IOException;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import java.util.Arrays;



public class NvaProbeDaemon implements Daemon {
	
	
	private Thread thread; 
	private Thread probeThread;
    private boolean stopped = false;
    private boolean lastOneWasATick = false;
    private NvaProbeServer probe;
    private Object monitor = new Object();
    private int socketPort;
    
    
    
    
    @Override
    public void init(DaemonContext daemonContext) throws DaemonInitException, Exception {
        /*
         * Construct objects and initialize variables here.
         * You can access the command line arguments that would normally be passed to your main() 
         * method as follows:
         */
        String[] args = daemonContext.getArguments(); 
        System.out.println("nva started");
      
        int arrindex = Arrays.asList(args).indexOf("port");
        
        if( arrindex > -1 )
        	socketPort = Integer.parseInt(args[arrindex+1]);
        else
        	socketPort = 9010;
        	
        thread = new Thread(){
          
            @Override
            public synchronized void start() {
            	try{
            	probe = new NvaProbeServer(socketPort);
            	NvaProbeDaemon.this.stopped = false;
                super.start();
               
            	}
            	catch (Exception exception)
            	{
            		 System.err.println(exception.getStackTrace());
            	}
            	
            	
            }

            @Override
            public void run() {
            	try{
	            	System.out.println("Starting Probe");
	            	// we need to run the server socket in another thread
	            	// otherwise this main thread blocks
	            	// probe implements runnable
	            	probeThread = new Thread(probe);
	            	probeThread.start();
	               // probe.start();
	                System.out.println("Probe started");
	                while(!stopped){
	                	  synchronized (monitor) {
	                            try {
	                                System.out.println("Waiting on monitor");
	                                monitor.wait();
	                            } catch (InterruptedException exception) {
	                            	 System.err.println(exception.getStackTrace());
	                                // Continue and check flag.
	                            }
	                            finally{
	                            	// we interrupt so the runnable stops
	                            	// listening on the socket 
	                            	probeThread.interrupt();
	                            }
	                        }
	                }
	                
	                System.out.println("Closing Probe");
                    probe.close();
                    System.out.println("Daemon shutdown complete");
            	}
            	catch (IOException e) {
                    System.err.println(e.getMessage());
                  }
            	
            }
        };
    }

    
   
    @Override
    public void start() throws Exception {
        thread.start();             
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Stopping daemon");
        stopped = true;
        //thread.interrupt();
        synchronized (monitor) {
            System.out.println("Notifying monitor");
            monitor.notify();
            System.out.println("Notified monitor");
        }
        try {
            thread.join();
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
            throw e;
        }
        System.out.print("Daemon stopped");
    }
    
    @Override
    public void destroy() {
        thread = null;
    }

	  

	
	

}
