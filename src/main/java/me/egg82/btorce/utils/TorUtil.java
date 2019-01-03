package me.egg82.btorce.utils;

import com.subgraph.orchid.TorClient;
import java.io.File;

public class TorUtil {
    private TorUtil() {}

    public static TorClient getClient(int index, int port, File currentDirectory) {
        TorClient client = new TorClient();
        client.getConfig().setDataDirectory(new File(currentDirectory, "cache-" + index));
        client.disableDashboard();
        client.enableSocksListener(port);

        return client;
    }
}
