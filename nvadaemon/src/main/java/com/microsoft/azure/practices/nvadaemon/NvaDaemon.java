package com.microsoft.azure.practices.nvadaemon;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NvaDaemon implements Daemon {
    private static final Logger LOG = LoggerFactory.getLogger(NvaDaemon.class);
    private Thread thread;
    private volatile boolean stopped;
    private Object monitor = new Object();
    private NvaDaemonConfig config;
    private CuratorFramework client;
    private ReentrantLock lock = new ReentrantLock();
    private Condition shutdown = lock.newCondition();

    public NvaDaemon(NvaDaemonConfig config, CuratorFramework client) {
        if (config == null) {
            throw new IllegalArgumentException("Value for config must be provided");
        }

        if (client == null) {
            throw new IllegalArgumentException("Value for client must be provided");
        }

        this.config = config;
        this.client = client;
    }

    @Override
    public void init(DaemonContext daemonContext) throws DaemonInitException {
//        String[] args = daemonContext.getArguments();
//        final NvaDaemonConfig cfg = new NvaDaemonConfig();
//        try {
//            if (args.length == 0) {
//                String msg = "Configuration file not specified";
//                LOG.error(msg);
//                throw new DaemonInitException(msg);
//            }
//
//            cfg.parse(args[0]);
//        }
//        catch (ConfigException e) {
//            String msg = "Invalid config";
//            LOG.error(msg);
//            throw new DaemonInitException(msg, e);
//        }
        thread = new Thread() {
            //private CuratorFramework client;
            private NvaLeaderSelectorListenerAdapter adapter;
            //private static final String ZK_NAMESPACE = "nvadaemon";

//            private synchronized void createClient() throws InterruptedException {
//                LOG.debug("Creating CuratorFramework");
//                client = CuratorFrameworkFactory.builder()
//                    .connectString(config.getConnectionString())
//                    .retryPolicy(new ExponentialBackoffRetry(1000, 10))
//                    .namespace(ZK_NAMESPACE)
//                    .build();
//                LOG.debug("Starting CuratorFramework");
//                client.start();
//                LOG.debug("Waiting for Zookeeper connection");
//                client.blockUntilConnected();
//                LOG.debug("Connected to Zookeeper");
//            }

            @Override
            public synchronized void start() {
                NvaDaemon.this.stopped = false;
                try {
                    //createClient();
//                client = CuratorFrameworkFactory.newClient(
//                        "localhost:2181", new ExponentialBackoffRetry(1000, 10)
//                );
                    //System.out.println("Starting client");
                    //client.start();
                    //client.usingNamespace(ZK_NAMESPACE);
                    //client.newNamespaceAwareEnsurePath(ZK_NAMESPACE);
                    //updateConfig(client, InetAddress.getLocalHost().getHostName());
                    adapter = new NvaLeaderSelectorListenerAdapter(client, "/leader-election");
                    super.start();
                }
                catch (Exception e) {
                    LOG.error("Exception starting Daemon thread", e);
                    throw new IllegalThreadStateException("Exception starting Daemon thread");
                    // Not much we can do. :(
                    // We may want to retool and work with Callable
                    //System.err.println(e.getStackTrace());
                }
            }

            @Override
            public void run() {
                try {
                    LOG.debug("Starting leader selector adapter");
                    adapter.start();
                    LOG.debug("Leader selector adapter started");
                    while (!stopped) {
                        lock.lock();
                        try {
                            LOG.debug("Waiting on signal");
                            shutdown.await();
                            LOG.debug("Signal received");
                        } catch (InterruptedException e) {
                            LOG.warn("lock interrupted", e);
                        } finally {
                            lock.unlock();
                        }
//                        synchronized (monitor) {
//                            try {
//                                LOG.debug("Waiting on monitor");
//                                monitor.wait();
//                                LOG.debug("Finished waiting on monitor");
//                                //Thread.sleep(5000);
//                            } catch (InterruptedException e) {
//                                // Continue and check flag.
//                                LOG.warn("Monitor interrupted", e);
//                            }
//                        }
                    }

//                    System.out.println("Closing adapter");
//                    adapter.close();
//                    System.out.println("Closing client");
//                    client.close();
//                    System.out.println("Daemon shutdown complete");
                }
                finally {
                    LOG.info("Closing leader selector adapter");
                    adapter.close();
                    //LOG.info("Closing CuratorFramework");
                    //client.close();
                }

                LOG.info("Daemon shutdown complete");
            }
        };
    }

    @Override
    public void start() throws Exception {
        LOG.debug("Starting daemon thread");
        thread.start();
    }

    @Override
    public void stop() throws Exception {
        LOG.info("Stopping daemon");
        stopped = true;
        //thread.interrupt();
        lock.lock();
        try {
            LOG.debug("Signalling condition");
            shutdown.signal();
            LOG.debug("Signalled condition");
        } finally {
            lock.unlock();
        }
//        synchronized (monitor) {
//            LOG.debug("Notifying monitor");
//            monitor.notify();
//            LOG.debug("Notified monitor");
//        }
        try {
            LOG.debug("Joining daemon thread");
            thread.join();
        } catch (InterruptedException e) {
            LOG.warn("Interrupted joining daemon thread: " + e.getMessage());
            throw e;
        }

        LOG.info("Daemon stopped");
    }

    @Override
    public void destroy() {
        thread = null;
    }
}