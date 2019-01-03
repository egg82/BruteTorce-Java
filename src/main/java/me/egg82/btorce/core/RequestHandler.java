package me.egg82.btorce.core;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.subgraph.orchid.TorClient;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.concurrent.*;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import me.egg82.btorce.services.CachedConfigValues;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Socket socket;
    private final TorClient client;

    private final ExecutorService threads = Executors.newFixedThreadPool(2, new ThreadFactoryBuilder().setNameFormat("RequestHandler-%d").build());

    private volatile boolean connected = true;

    public RequestHandler(Socket socket, TorClient client) {
        this.socket = socket;
        this.client = client;

        CachedConfigValues cachedConfig;
        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        if (cachedConfig.getDebug()) {
            logger.debug("New request handler created");
        }
    }

    // https://github.com/stefano-lupo/Java-Proxy-Server/blob/master/src/RequestHandler.java
    public void start() {
        CachedConfigValues cachedConfig;
        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        try {
            InputStream socketInput = socket.getInputStream();
            OutputStream socketOutput = socket.getOutputStream();

            ByteArrayOutputStream stringBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream trueBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream postBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            outer:
            while ((bytesRead = socketInput.read(buffer)) > -1) {
                trueBuffer.write(buffer, 0, bytesRead);
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\r') {
                        stringBuffer.write(buffer, 0, i);
                        if (bytesRead - i > 0) {
                            postBuffer.write(buffer, i, bytesRead - i);
                        }
                        break outer;
                    }
                }
                stringBuffer.write(buffer, 0, bytesRead);
            }

            String request = new String(stringBuffer.toByteArray(), Charset.forName("ASCII"));
            if (cachedConfig.getDebug()) {
                logger.debug("Request: " + request);
            }

            boolean isHttps;
            boolean sendHeader;
            String host;
            int port;

            if (request.startsWith("CONNECT")) {
                // HTTPS/random connections

                sendHeader = false;
                isHttps = true;

                String[] explodedRequest = request.split("\\s+");
                if (explodedRequest.length < 2) {
                    if (cachedConfig.getDebug()) {
                        logger.debug("Request header was not required length. Closing.");
                        socket.close();
                        return;
                    }
                }

                host = explodedRequest[1];
                if (host.indexOf(':') > -1) {
                    port = Integer.parseInt(host.substring(host.indexOf(':') + 1));
                    host = host.substring(0, host.indexOf(':'));
                } else {
                    port = 443;
                }
            } else {
                // HTTP (or HTTPS if we're being dumb)

                sendHeader = true;

                String[] explodedRequest = request.split("\\s+");
                if (explodedRequest.length < 2 || (!explodedRequest[1].startsWith("http://") && !explodedRequest[1].startsWith("https://"))) {
                    if (cachedConfig.getDebug()) {
                        logger.debug("Request header was not HTTP or HHTPS. Closing.");
                        socket.close();
                        return;
                    }
                }

                isHttps = explodedRequest[1].charAt(explodedRequest[1].indexOf(':') - 1) == 's';

                host = explodedRequest[1].substring(explodedRequest[1].indexOf(':') + 3);
                if (host.indexOf('/') > -1) {
                    host = host.substring(0, host.indexOf('/'));
                }

                if (host.indexOf(':') > -1) {
                    port = Integer.parseInt(host.substring(host.indexOf(':') + 1));
                    host = host.substring(0, host.indexOf(':'));
                } else {
                    port = isHttps ? 443 : 80;
                }
            }

            if (cachedConfig.getDebug()) {
                if (cachedConfig.getTimeout() > 0) {
                    logger.debug("Connecting to: " + host + " on port " + port + " (timeout at " + cachedConfig.getTimeout() + " seconds)");
                } else {
                    logger.debug("Connecting to: " + host + " on port " + port);
                }
            }

            final String threadHost = host;
            SSLSocket sslTor = null;
            Socket tor;
            try {
                tor = CompletableFuture.supplyAsync(() -> {
                    try {
                        return client.getSocketFactory().createSocket(threadHost, port);
                    } catch (IOException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                    return null;
                }).get(cachedConfig.getTimeout(), TimeUnit.SECONDS);
                if (isHttps) {
                    sslTor = CompletableFuture.supplyAsync(() -> {
                        try {
                            if (cachedConfig.getDebug()) {
                                logger.debug("Creating SSL socket wrapper");
                            }
                            SSLSocket retVal = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(client.getSocketFactory().createSocket(threadHost, port), null, port, false);
                            retVal.setUseClientMode(true);
                            return retVal;
                        } catch (IOException ex) {
                            logger.error(ex.getMessage(), ex);
                        }
                        return null;
                    }).get(cachedConfig.getTimeout(), TimeUnit.SECONDS);
                }
            } catch (ExecutionException ex) {
                logger.error(ex.getMessage(), ex);
                socket.close();
                return;
            } catch (TimeoutException ignored) {
                if (cachedConfig.getDebug()) {
                    logger.debug("Connection to " + host + " on " + port + " timed out");
                }
                socket.close();
                return;
            }
            InputStream torInput = tor.getInputStream();
            OutputStream torOutput = tor.getOutputStream();

            if (cachedConfig.getDebug()) {
                logger.debug("Proxying to: " + host + " on " + port);
            }

            // Replay the request
            if (sendHeader) {
                torOutput.write(trueBuffer.toByteArray());
            } else {
                OutputStream sslTorOutput = sslTor.getOutputStream();
                InputStream sslTorInput = sslTor.getInputStream();

                String getRequest = request.replace("CONNECT", "GET");
                String[] getExploded = getRequest.split("\\s+");
                String url = getExploded[1].indexOf('/') > -1 ? getExploded[1].substring(getExploded[1].indexOf('/') + 1) : "";
                getExploded[1] = "https://" + host + ":" + port + "/" + url;
                getRequest = String.join(" ", getExploded);

                ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
                headerBuffer.write(getRequest.getBytes(Charset.forName("ASCII")));
                postBuffer.writeTo(headerBuffer);

                sslTorOutput.write(headerBuffer.toByteArray());
                sslProxy(sslTor, sslTorInput, socketOutput);
            }
            torOutput.flush();

            threads.execute(() -> proxy(socketInput, torOutput));
            threads.execute(() -> proxy(torInput, socketOutput));

            while (connected) {
                Thread.sleep(250L);
            }

            if (cachedConfig.getDebug()) {
                logger.debug("Disconnected from: " + host + " on " + port);
            }

            tor.close();
            socket.close();
        } catch (SocketException ex) {
            if (!ex.getMessage().equals("Socket closed") && !ex.getMessage().equals("Connection reset")) {
                logger.error(ex.getMessage(), ex);
            }
        } catch (IOException ex) {
            if (!ex.getMessage().endsWith("stream closed")) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                socket.close();
            } catch (IOException ignored) {}
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void proxy(InputStream from, OutputStream to) {
        byte[] buffer = new byte[4096];
        int bytesRead;

        try {
            while ((bytesRead = from.read(buffer)) > -1) {
                to.write(buffer, 0, bytesRead);
                to.flush();
            }
        } catch (SocketException ex) {
            if (!ex.getMessage().equals("Socket closed") && !ex.getMessage().equals("Connection reset")) {
                logger.error("[Socket] " + ex.getMessage(), ex);
            }
        } catch (IOException ex) {
            if (!ex.getMessage().endsWith("stream closed")) {
                logger.error("[Socket] " + ex.getMessage(), ex);
            }
        }

        connected = false;
    }

    private void sslProxy(SSLSocket sslTor, InputStream from, OutputStream to) {
        CachedConfigValues cachedConfig;
        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        ByteArrayOutputStream stringBuffer = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];
        int bytesRead;

        try {
            while ((bytesRead = from.read(buffer)) > -1) {
                boolean done = false;

                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\n') {
                        to.write(buffer, 0, i);
                        to.flush();

                        stringBuffer.write(buffer, 0, i + 1);
                        done = true;
                        break;
                    }
                }
                if (done) {
                    String line = new String(stringBuffer.toByteArray(), Charset.forName("ASCII"));
                    if (cachedConfig.getDebug()) {
                        logger.debug("[SSL] " + line.substring(0, line.length() - 2));
                    }
                    stringBuffer.reset();
                    break;
                } else {
                    to.write(buffer, 0, bytesRead);
                    to.flush();
                    stringBuffer.write(buffer, 0, bytesRead);
                }
            }

            to.write("Proxy-agent: ProxyServer/1.0\r\n\r\n".getBytes(Charset.forName("ASCII")));
            to.flush();
        } catch (SocketException ex) {
            if (!ex.getMessage().equals("Socket closed") && !ex.getMessage().equals("Connection reset")) {
                logger.error("[SSL] " + ex.getMessage(), ex);
            }
        } catch (IOException ex) {
            if (!ex.getMessage().endsWith("stream closed")) {
                logger.error("[SSL] " + ex.getMessage(), ex);
            }
        }

        logger.debug("[SSL] Connection closed");

        try {
            sslTor.close();
        } catch (IOException ignored) {}
    }
}
