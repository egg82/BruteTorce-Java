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
                .build();

        if (debug) {
            /*logger.debug("Sharding: " + config.getNode("discord", "sharding", "enabled").getBoolean(false));
            if (config.getNode("discord", "sharding", "enabled").getBoolean(false)) {
                logger.debug("Sharding index: " + config.getNode("discord", "sharding", "index").getInt(0));
                logger.debug("Sharding count: " + config.getNode("discord", "sharding", "count").getInt(1000));
            }

            logger.debug("Command prefix: \"" + cachedValues.getCommandConfig().getPrefix() + "\"");
            logger.debug("Commands from bots ignored: " + cachedValues.getCommandConfig().getIgnoreBots());*/
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
