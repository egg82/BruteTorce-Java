package me.egg82.btorce.core;

import com.subgraph.orchid.TorClient;
import java.io.File;
import me.egg82.btorce.utils.TorUtil;

public class TorClientWrapper {
    private final int index;
    private final int port;
    private final TorClient client;

    public TorClientWrapper(int index, int port, File currentDirectory) {
        this.index = index;
        this.port = port;
        this.client = TorUtil.getClient(index, port, currentDirectory);
    }

    public int getIndex() { return index; }

    public int getPort() { return port; }

    public TorClient getClient() { return client; }
}
