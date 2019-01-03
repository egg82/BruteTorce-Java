package me.egg82.btorce.core;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.subgraph.orchid.TorClient;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.egg82.btorce.services.CachedConfigValues;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Proxy {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Queue<TorClient> clients;
    private final File currentDirectory;

    private final ServerSocket server;

    private final ExecutorService listenThread = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Proxy-%d").build());
    private final ExecutorService threadPool;

    private volatile boolean running = true;

    public Proxy(int port, int numThreads, Collection<TorClient> clients, File currentDirectory) throws IOException {
        this.clients = new ConcurrentLinkedQueue<>(clients);
        this.currentDirectory = currentDirectory;

        this.threadPool = Executors.newWorkStealingPool(numThreads);

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
                TorClient client = getNextClient();
                if (cachedConfig.getDebug()) {
                    logger.debug("New connection accepted");
                }
                threadPool.execute(() -> {
                    new RequestHandler(socket, client).start();
                    clients.add(client);
                });
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }
    }

    private TorClient getNextClient() {
        TorClient client;
        do {
            client = clients.poll();
        } while (client == null);
        return client;
    }
}
