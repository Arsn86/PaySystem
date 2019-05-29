package ru.dz.pay.system.database;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ToString
@Component
@ConfigurationProperties
public class ConnectionSettings {

    private static final Logger log = LogManager.getLogger(ConnectionSettings.class);
    private static int DEFAULT_MAX_POOL_SIZE = 5;

    private String jdbcDriver = "com.mysql.cj.jdbc.Driver";
    private String jdbcString = "jdbc:mysql://192.168.31.13:3306/test?serverTimezone=UTC&useSSL=false";
    private String jdbcUser = "user";
    private String jdbcPassword = "afga4eg5sSd4Q";
    private boolean autoCommit = false;

    private int jdbcMaxPoolSize = DEFAULT_MAX_POOL_SIZE;

    public ConnectionSettings() {
    }

}
