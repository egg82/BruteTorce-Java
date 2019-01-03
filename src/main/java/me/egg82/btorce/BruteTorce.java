package me.egg82.btorce;

import com.subgraph.orchid.TorClient;
import com.subgraph.orchid.TorInitializationListener;
import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import me.egg82.btorce.core.Proxy;
import me.egg82.btorce.core.TorClientWrapper;
import me.egg82.btorce.services.CachedConfigValues;
import me.egg82.btorce.services.Configuration;
import me.egg82.btorce.utils.ConfigurationFileUtil;
import me.egg82.btorce.utils.TorUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BruteTorce {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final File currentDirectory;

    private final List<TorClientWrapper> loadingClients = new ArrayList<>();
    private final List<TorClientWrapper> readyClients = new ArrayList<>();
    private Proxy proxy;

    public BruteTorce(File currentDirectory) {
        this.currentDirectory = currentDirectory;

        loadServices();
        loadTor();

        start();
    }

    private void loadServices() {
        logger.info("Loading services..");
        ConfigurationFileUtil.reloadConfig(currentDirectory);
    }

    // https://stackoverflow.com/questions/29171643/java-tor-lib-how-to-setup-orchid-tor-lib-with-java
    private void loadTor() {
        logger.info("Loading Tor..");

        Configuration config;
        CachedConfigValues cachedConfig;
        try {
            config = ServiceLocator.get(Configuration.class);
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        int clients = config.getNode("tor", "connections").getInt(10);
        for (int i = 0; i < clients; i++) {
            int port = getPort();
            logger.info("[" + i + "]: SOCKS at " + port);
            TorClientWrapper client = new TorClientWrapper(i, port, currentDirectory);
            loadingClients.add(client);

            final int clientNum = i;

            client.getClient().addInitializationListener(new TorInitializationListener() {
                public void initializationProgress(String message, int percent) {
                    if (cachedConfig.getDebug()) {
                        logger.debug("[" + clientNum + "] [" + percent + "%]: " + message);
                    }
                }
                public void initializationCompleted() {
                    logger.info("[" + clientNum + "]: Circuit complete!");

                    loadingClients.remove(client);
                    readyClients.add(client);
                }
            });
        }
    }

    private void start() {
        logger.info("Staring Tor..");

        for (TorClientWrapper client : loadingClients) {
            client.getClient().start();
        }

        logger.info("Waiting for all clients to start..");

        while (!loadingClients.isEmpty()) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Starting proxy..");

        Configuration config;
        try {
            config = ServiceLocator.get(Configuration.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        int port = config.getNode("tor", "port").getInt(13860);
        int threads = config.getNode("tor", "connections").getInt(10);

        try {
            proxy = new Proxy(port, threads, readyClients, currentDirectory);
        } catch (IOException ignored) {}

        do {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        } while (true);
    }

    private int getPort() {
        int port;
        do {
            port = fairRoundedRandom(1025, 65534);
        } while (!available(port));
        return port;
    }

    private static int fairRoundedRandom(int min, int max) {
        int num;
        max++;

        do {
            num = (int) Math.floor(Math.random() * (max - min) + min);
        } while (num > max - 1);

        return num;
    }

    // https://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
    public static boolean available(int port) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid start port: " + port);
        }

        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
        } finally {
            if (ds != null) {
                ds.close();
            }

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    /* should not be thrown */
                }
            }
        }

        return false;
    }
}
