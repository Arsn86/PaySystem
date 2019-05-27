package ru.dz.pay.system;

import lombok.Getter;
import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

@ToString
@Getter
public class Configuration {

    private static final Logger log = LogManager.getLogger(Client.class);

    private static boolean complete = false;

    private String confFile = "conf/param.prop";
    private String basePath = "";
    private String url = "http://localhost";
    private int threads = 0;
    private int count = 0;
    public int connectTimeout = 10000;
    public int socketTimeout = 14000;

    @ToString
    @Getter
    public class ProfileConf {
        private boolean use;
        private String path;
        private boolean random;
    }

    public Configuration() {
        confFile = System.getProperty("ConfigFile", confFile);
    }

    public Configuration(String confFile) {
        this.confFile = confFile;
    }

    public synchronized void readConfig() {
        log.info("Read config file: " + confFile + "\nComplete status = " + complete);
        if (!complete) {
            Properties prop = new Properties();
            File configFile = new File(basePath + confFile);
            try {
                Properties properties = new Properties();
                properties.load(new FileInputStream(configFile));
                properties.forEach((k, v) -> prop.put(k.toString().toUpperCase(), v));
            } catch (IOException e) {
                log.error("Error read config file! Exception: " + e);
                throw new RuntimeException("Error read config file!");
            }

            url = prop.getProperty("URL", url).trim();
            threads = Integer.valueOf(prop.getProperty("THREADCOUNT", "0"));
            count = Integer.valueOf(prop.getProperty("MESSAGECOUNT", "0"));
            connectTimeout = Integer.valueOf(prop.getProperty("CONNECTTIMEOUT", String.valueOf(connectTimeout)));
            socketTimeout = Integer.valueOf(prop.getProperty("SOCKETTIMEOUT", String.valueOf(socketTimeout)));


            complete = true;
        }
        log.info("Read config file: " + confFile + " complete!\nStatus: " + complete + "\nConfiguration:\n" + this.toString());
    }

    public void setBasePath(String path) {
        basePath = path;
    }

    public String getHost() {
        String result = "";

        if (url != null && url.length() > 0) {
            try {
                URI uri = new URI(url);
                result = uri.getHost();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        return result;
    }


    public int getPort() {
        int port = 0;

        if (url != null && url.length() > 0) {
            try {
                URI uri = new URI(url);
                port = uri.getPort();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        return port;
    }


}

