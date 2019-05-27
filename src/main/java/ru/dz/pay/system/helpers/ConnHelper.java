package ru.dz.pay.system.helpers;

import ru.dz.pay.system.helpers.database.ConnectionSettings;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnHelper {

    private ConnectionSettings connectionSettings = new ConnectionSettings();

    private Connection connection;

    public Connection getConnection() {
        String url = connectionSettings.getJdbcString() + "&user=" + connectionSettings.getJdbcUser() + "&password=" + connectionSettings.getJdbcPassword();

        try {
            //Driver driver = (Driver) Class.forName("com.mysql.cj.jdbc.Driver").getConstructor().newInstance();
            Driver driver = new com.mysql.cj.jdbc.Driver();
            DriverManager.registerDriver(driver);

            connection = DriverManager.getConnection(url);

            connection.setAutoCommit(true);

            System.out.println("Autocommit enabled = " + connection.getAutoCommit());

            return connection;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
