package ru.dz.pay.system.helpers.executor;

import java.sql.*;

public class Executor {

    private final Connection connection;

    public Executor(Connection connection) {
        this.connection = connection;
    }

    public void execQuery(String sqlQuery) throws SQLException {
        System.out.println(sqlQuery);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sqlQuery);
        }
    }

    public void execQuery(String sqlQuery, ExecuteHandler executeHandler) throws SQLException, IllegalAccessException {
        try (PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            executeHandler.accept(statement);
        }
    }

    public <T> T execQuery(String sqlQuery, ResultHandler<T> handler) throws SQLException, InstantiationException, IllegalAccessException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sqlQuery);
            try (ResultSet result = statement.getResultSet()) {
                return handler.handle(result);
            }
        }
    }

    public <T> T execQuery(String sqlQuery, ExecuteHandler executeHandler, ResultHandler<T> handler) throws SQLException, InstantiationException, IllegalAccessException {
        try (PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            executeHandler.accept(statement);
            try (ResultSet result = statement.getResultSet()) {
                return handler.handle(result);
            }
        }
    }

}
