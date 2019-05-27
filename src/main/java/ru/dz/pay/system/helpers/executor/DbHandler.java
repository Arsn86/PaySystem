package ru.dz.pay.system.helpers.executor;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface DbHandler {
    void handle(ResultSet result) throws SQLException;
}
