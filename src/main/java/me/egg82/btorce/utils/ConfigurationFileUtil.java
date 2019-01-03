package me.egg82.btorce.utils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Optional;
import me.egg82.btorce.services.CachedConfigValues;
import me.egg82.btorce.services.Configuration;
import ninja.egg82.service.ServiceLocator;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;

public class ConfigurationFileUtil {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationFileUtil.class);

    private ConfigurationFileUtil() {}

    public static void reloadConfig(File currentDirectory) {
        Configuration config;
        try {
            config = getConfig("config.yml", new File(currentDirectory, "config.yml"));
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        boolean debug = config.getNode("debug").getBoolean(false);

        if (debug) {
            logger.debug("Debug enabled");
        }

        try {
            destroyServices(ServiceLocator.getOptional(CachedConfigValues.class));
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error(ex.getMessage(), ex);
        }

        CachedConfigValues cachedValues = CachedConfigValues.builder()
                .debug(debug)
                .timeout(config.getNode("tor", "timeout").getInt(20))
                .maxUse(config.getNode("tor", "max-use").getInt(5))
                .build();

        if (debug) {
            logger.debug("Connections: " + config.getNode("tor", "connections").getInt(10));
            logger.debug("Timeout: " + cachedValues.getTimeout());
            logger.debug("Max-Use: " + cachedValues.getMaxUse());
            logger.debug("Listen port: " + config.getNode("tor", "port").getInt(13860));
        }

        ServiceLocator.register(config);
        ServiceLocator.register(cachedValues);
    }

    public static Configuration getConfig(String resourcePath, File fileOnDisk) throws IOException {
        File parentDir = fileOnDisk.getParentFile();
        if (parentDir.exists() && !parentDir.isDirectory()) {
            Files.delete(parentDir.toPath());
        }
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Could not create parent directory structure.");
            }
        }
        if (fileOnDisk.exists() && fileOnDisk.isDirectory()) {
            Files.delete(fileOnDisk.toPath());
        }

        if (!fileOnDisk.exists()) {
            try (InputStreamReader reader = new InputStreamReader(getResource(resourcePath));
                 BufferedReader in = new BufferedReader(reader);
                 FileWriter writer = new FileWriter(fileOnDisk);
                 BufferedWriter out = new BufferedWriter(writer)) {
                String line;
                while ((line = in.readLine()) != null) {
                    out.write(line + System.lineSeparator());
                }
            }
        }

        ConfigurationLoader<ConfigurationNode> loader = YAMLConfigurationLoader.builder().setFlowStyle(DumperOptions.FlowStyle.BLOCK).setIndent(2).setFile(fileOnDisk).build();
        ConfigurationNode root = loader.load(ConfigurationOptions.defaults().setHeader("Comments are gone because update :(. Click here for new config + comments: https://www.spigotmc.org/resources/anti-vpn.58291/"));
        Configuration config = new Configuration(root);
        ConfigurationVersionUtil.conformVersion(loader, config, fileOnDisk);

        return config;
    }

    private static void destroyServices(Optional<CachedConfigValues> cachedConfigValues) {
        if (!cachedConfigValues.isPresent()) {
            return;
        }

        //cachedConfigValues.get().getSQL().close();
    }

    private static InputStream getResource(String filename) {
        try {
            URL url = ConfigurationFileUtil.class.getClassLoader().getResource(filename);

            if (url == null) {
                return null;
            }

            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            return connection.getInputStream();
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }
    }

    private static double clamp(double min, double max, double val) { return Math.min(max, Math.max(min, val)); }
}
