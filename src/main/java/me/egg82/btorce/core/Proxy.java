package me.egg82.btorce.core;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.subgraph.orchid.TorClient;
import com.subgraph.orchid.TorInitializationListener;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.*;
import me.egg82.btorce.services.CachedConfigValues;
import me.egg82.btorce.utils.TorUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Proxy {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Queue<TorClientWrapper> clients;
    private final ConcurrentMap<Integer, Integer> clientUsageMap = new ConcurrentHashMap<>();
    private final File currentDirectory;

    private final ServerSocket server;

    private final ExecutorService listenThread = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Proxy-%d").build());
    private final ExecutorService threadPool;

    private volatile boolean running = true;

    public Proxy(int port, int numThreads, Collection<TorClientWrapper> clients, File currentDirectory) throws IOException {
        this.clients = new ConcurrentLinkedQueue<>(clients);
        this.currentDirectory = currentDirectory;

        this.threadPool = Executors.newWorkStealingPool(numThreads);

        for (TorClientWrapper client : clients) {
            clientUsageMap.put(client.getPort(), 0);
        }

        try {
            this.server = new ServerSocket(port);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        listenThread.execute(this::listen);
    }

    private void listen() {
        CachedConfigValues cachedConfig;
        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        while (running) {
            try {
                Socket socket = server.accept();
                TorClientWrapper client = getNextClient();
                if (cachedConfig.getDebug()) {
                    logger.debug("New connection accepted");
                }
                threadPool.execute(() -> {
                    new RequestHandler(socket, client.getClient()).start();
                    Integer result = clientUsageMap.compute(client.getPort(), (k, v) -> {
                        if (v == null) {
                            v = 0;
                        }

                        v += 1;

                        if (v >= cachedConfig.getMaxUse()) {
                            return 0;
                        }
                        return v;
                    });

                    if (result > 0) {
                        clients.add(client);
                        return;
                    }

                    if (cachedConfig.getDebug()) {
                        logger.debug("[" + client.getIndex() + "]: Creating new route");
                    }

                    client.getClient().stop();
                    TorClientWrapper newClient = new TorClientWrapper(client.getIndex(), client.getPort(), currentDirectory);
                    newClient.getClient().addInitializationListener(new TorInitializationListener() {
                        public void initializationProgress(String message, int percent) {
                            if (cachedConfig.getDebug()) {
                                logger.debug("[" + newClient.getIndex() + "] [" + percent + "%]: " + message);
                            }
                        }
                        public void initializationCompleted() {
                            logger.info("[" + newClient.getIndex() + "]: Circuit complete!");

                            clients.add(newClient);
                        }
                    });
                    newClient.getClient().start();
                });
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }
    }

    private TorClientWrapper getNextClient() {
        TorClientWrapper client;
        do {
            client = clients.poll();
        } while (client == null);
        return client;
    }
}
