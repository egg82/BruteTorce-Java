package me.egg82.btorce.utils;

import com.subgraph.orchid.TorClient;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorUtil {
    private static final Logger logger = LoggerFactory.getLogger(TorUtil.class);

    private TorUtil() {}

    public static TorClient getClient(int index, int port, File currentDirectory) {
        File cacheDir = new File(currentDirectory, "cache-" + index);
        if (cacheDir.exists() && cacheDir.isFile()) {
            try {
                Files.delete(cacheDir.toPath());
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }

        if (!cacheDir.exists()) {
            try {
                FileUtils.copyDirectory(new File(currentDirectory, "cache-master"), cacheDir);
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }

            File stateFile = new File(cacheDir, "state");
            if (stateFile.exists()) {
                try {
                    Files.delete(stateFile.toPath());
                } catch (IOException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }
        }

        TorClient client = new TorClient();
        client.getConfig().setDataDirectory(new File(currentDirectory, "cache-" + index));
        client.disableDashboard();
        client.enableSocksListener(port);

        return client;
    }
}
