package ru.dz.pay.system.helpers;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnHelper {

    private Connection connection;

    public Connection getConnection() {
        String url = "jdbc:mysql://192.168.31.13:3306/test?user=user&password=afga4eg5sSd4Q&serverTimezone=UTC&useSSL=false";

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
