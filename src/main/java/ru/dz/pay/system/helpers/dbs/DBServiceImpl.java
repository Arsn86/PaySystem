package ru.dz.pay.system.helpers.dbs;

import ru.dz.pay.system.helpers.ConnHelper;
import ru.dz.pay.system.helpers.data.DataSet;
import ru.dz.pay.system.helpers.executor.Executor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//@SuppressWarnings("unchecked")
public class DBServiceImpl implements DBService {

    private final Connection connection;
    private final ConnHelper connHelper;

    private final static String CREATE_TABLE_ACCOUNTS = "create table if not exists `accounts` (id bigint auto_increment, balance int(11) unsigned not null, primary key (id));";
    private final static String CREATE_TABLE_ACCOUNT_HISTORY = "create table if not exists `account_history` (id bigint auto_increment, account_id int(11)" +
            ", dt datetime, amount int(11),type int(1),result tinyint, transaction_id bigint, primary key (id));";

    private final static String INSERT_ACCOUNT = "insert into accounts (balance) values (?)";
    private final static String INSERT_MAIN_ACCOUNT = "insert into accounts (id, balance) values (?,?)";

    private final static String SELECT_ACCOUNT_ID = "select * from accounts where id = (?)";
    private final static String SELECT_ACCOUNTS = "select * from accounts";
    private final static String SELECT_ACCOUNTS_COUNT = "select count(*) from accounts";

    private final static String DROP_TABLE_ACCOUNTS = "DROP TABLE if exists `accounts`;";
    private final static String DROP_TABLE_ACCOUNT_HISTORY = "DROP TABLE if exists `account_history`;";

    public DBServiceImpl() {
        connHelper = new ConnHelper();
        connection = connHelper.getConnection();
    }

    @Override
    public <T extends DataSet> void save(T account) throws SQLException, IllegalAccessException, NoSuchFieldException {
        Executor exec = new Executor(connection);
        Field[] fields = account.getClass().getDeclaredFields();
        exec.execQuery(INSERT_ACCOUNT, statement -> {
            System.out.println(INSERT_ACCOUNT);
            statement.execute();
        });
    }

    public <T extends DataSet> void createMainBalance(int id, long balance) throws SQLException, IllegalAccessException, NoSuchFieldException {
        Executor exec = new Executor(connection);
        exec.execQuery(INSERT_MAIN_ACCOUNT, statement -> {
            statement.setInt(1, id);
            statement.setLong(2, balance);
            System.out.println(INSERT_MAIN_ACCOUNT);
            statement.execute();
        });
    }

    @Override
    public <T extends DataSet> T load(long id, Class<T> clazz) throws SQLException, IllegalAccessException, InstantiationException {
        Executor exec = new Executor(connection);
        return exec.execQuery(SELECT_ACCOUNT_ID,
                statement -> {
                    statement.setLong(1, id);
                    statement.execute();
                }, result -> {
                    if (result.next()) {
                        T user = clazz.newInstance();
                        List<Field> fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));

                        if (clazz.getSuperclass() != null) {
                            fields.addAll(Arrays.asList(clazz.getSuperclass().getDeclaredFields()));
                        }

                        for (Field field : fields) {
                            System.out.println("Field: " + field);
                            field.setAccessible(true);
                            field.set(user, result.getObject(field.getName()));
                        }
                        return user;
                    } else return null;
                });
    }

    @Override
    public <T extends DataSet> List<T> getAll(Class<T> clazz) throws SQLException, IllegalAccessException, InstantiationException {
        Executor exec = new Executor(connection);
        return exec.execQuery(SELECT_ACCOUNTS, result -> {
            List<T> list = new ArrayList<>();
            while (result.next()) {
                try {
                    list.add(clazz.getConstructor(long.class, String.class, int.class).newInstance(result.getLong("id"), result.getString("name"), result.getInt("age")));
                } catch (InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
                //list.add((T) new UserDataSet(result.getLong("id"), result.getString("name"), result.getInt("age")));
            }
            return list;
        });
    }


    @Override
    public long getCount() throws SQLException, IllegalAccessException, InstantiationException {
        return 0;
    }

    @Override
    public void createTables() throws SQLException {
        Executor executor = new Executor(connection);
        executor.execQuery(CREATE_TABLE_ACCOUNTS);
        executor.execQuery(CREATE_TABLE_ACCOUNT_HISTORY);
        //connHelper.closeConnection();
    }

    @Override
    public void dropTables() throws SQLException {
        Executor executor = new Executor(connection);
        executor.execQuery(DROP_TABLE_ACCOUNTS);
        executor.execQuery(DROP_TABLE_ACCOUNT_HISTORY);
        //connHelper.closeConnection();
    }

    @Override
    public void close() throws Exception {
        connection.close();
        System.out.println("Connection close!");
    }

}
