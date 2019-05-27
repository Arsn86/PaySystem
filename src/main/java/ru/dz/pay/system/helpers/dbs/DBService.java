package ru.dz.pay.system.helpers.dbs;

import ru.dz.pay.system.helpers.data.DataSet;

import java.sql.SQLException;
import java.util.List;

public interface DBService extends AutoCloseable {

    <T extends DataSet> void save(T user) throws SQLException, IllegalAccessException, NoSuchFieldException;

    <T extends DataSet> T load(long id, Class<T> clazz) throws SQLException, IllegalAccessException, InstantiationException;

    <T extends DataSet> List<T> getAll(Class<T> clazz) throws SQLException, IllegalAccessException, InstantiationException;

    long getCount() throws SQLException, IllegalAccessException, InstantiationException;

    void createTables() throws SQLException;

    void dropTables() throws SQLException;
}
