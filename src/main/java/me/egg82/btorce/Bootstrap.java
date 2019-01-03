package me.egg82.btorce;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import me.egg82.btorce.utils.JarUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bootstrap {
    public static void main(String[] args) { new Bootstrap(); }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Bootstrap() {
        logger.info("Starting..");

        try {
            loadJars(new File(getDirectory(), "external"), (URLClassLoader) getClass().getClassLoader());
        }  catch (ClassCastException | IOException | IllegalAccessException | InvocationTargetException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        new BruteTorce(getDirectory());
    }

    private void loadJars(File jarsFolder, URLClassLoader classLoader) throws IOException, IllegalAccessException, InvocationTargetException {
        if (jarsFolder.exists() && !jarsFolder.isDirectory()) {
            Files.delete(jarsFolder.toPath());
        }
        if (!jarsFolder.exists()) {
            if (!jarsFolder.mkdirs()) {
                throw new IOException("Could not create parent directory structure.");
            }
        }

        /*logger.info("Loading dep Guava");
        JarUtil.loadJar("http://central.maven.org/maven2/com/google/guava/guava/27.0.1-jre/guava-27.0.1-jre.jar",
                new File(jarsFolder, "guava-27.0.1-jre.jar"),
                classLoader);

        logger.info("Loading dep Javassist");
        JarUtil.loadJar("http://central.maven.org/maven2/org/javassist/javassist/3.23.1-GA/javassist-3.23.1-GA.jar",
                new File(jarsFolder, "javassist-3.23.1-GA.jar"),
                classLoader);*/
    }

    private File getDirectory() {
        try {
            return new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch (URISyntaxException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return null;
    }
}
