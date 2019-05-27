package ru.dz.pay.system.helpers.database;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import ru.dz.pay.system.Client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@Getter
@Setter
@ToString
@Component
public class ConnectionSettings {

    private static final Logger log = LogManager.getLogger(Client.class);

    private static int DEFAULT_MAX_POOL_SIZE = 5;

    private String jdbcDriver = "com.mysql.cj.jdbc.Driver";
    private String jdbcString = "jdbc:mysql://192.168.31.13:3306/test?serverTimezone=UTC&useSSL=false";
    private String jdbcUser = "user";
    private String jdbcPassword = "afga4eg5sSd4Q";
    private boolean autoCommit = false;

    private static boolean complete = false;
    private static String confFile = "conf/application.properties";
    private static String basePath = "";

    private int jdbcMaxPoolSize = DEFAULT_MAX_POOL_SIZE;

    public ConnectionSettings() {
        readConfig();
    }

    private synchronized void readConfig() {
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

            jdbcDriver = prop.getProperty("JDBCDRIVER", jdbcDriver);
            jdbcString = prop.getProperty("JDBCSTRING", jdbcString);
            jdbcUser = prop.getProperty("JDBCUSER", jdbcUser);
            jdbcPassword = prop.getProperty("JDBCPASSWORD", jdbcPassword);
            autoCommit = Boolean.valueOf(prop.getProperty("AUTOCOMMIT", String.valueOf(autoCommit)));

            complete = true;
        }
        log.info("Read config file: " + confFile + " complete!\nStatus: " + complete + " properties: " + this.toString());
    }

}
