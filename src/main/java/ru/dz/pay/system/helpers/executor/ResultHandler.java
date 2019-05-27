package ru.dz.pay.system.helpers.executor;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ResultHandler<T> {
    T handle(ResultSet result) throws SQLException, IllegalAccessException, InstantiationException;
}
