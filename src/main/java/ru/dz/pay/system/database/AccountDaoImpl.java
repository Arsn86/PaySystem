package ru.dz.pay.system.database;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class AccountDaoImpl implements AccountDao {

    private static final String SELECT_ACCOUNT_ID = "select * from accounts where id = :id";
    private static final String SELECT_ACCOUNTS = "select * from accounts;";
    private static final String SELECT_ACCOUNTS_COUNT = "select count(*) count from accounts;";
    private static final String UPDATE_ACCOUNT_ID = "update accounts set balance = :balance where id = :id";
    private static final String UPDATE_ACCOUNT_HISTORY = "insert into account_history (account_id, dt, amount, type, result, transaction_id)" +
            " values (:account_id,:dt,:amount,:type,:result,:transaction_id);";
    private static final String TRANSFER_BALANCE = "update accounts set balance = balance + :balance where id = :id";
    private static final String GET_ALL_BALANCE = "select sum(balance) balance from accounts;";

    private final AccountMapper accountMapper;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final PlatformTransactionManager platformTransactionManager;

    @Autowired
    public AccountDaoImpl(AccountMapper accountMapper, NamedParameterJdbcTemplate jdbcTemplate, DataSource dataSource, PlatformTransactionManager platformTransactionManager) {
        this.accountMapper = accountMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.platformTransactionManager = platformTransactionManager;
    }

    @Override
    public boolean commit() {
        try {
            dataSource.getConnection().commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Optional<Account> getAccountById(int id) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", id);
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_ACCOUNT_ID, params, accountMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Account> getAllAccounts() {
        return jdbcTemplate.query(SELECT_ACCOUNTS, accountMapper);
    }

    @Override
    public boolean updateAccountById(int id, int balance) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", id);
        params.addValue("balance", balance);
        Account account = getAccountById(id).isPresent() ? getAccountById(id).get() : null;

        DefaultTransactionDefinition paramTransactionDefinition = new DefaultTransactionDefinition();
        TransactionStatus status = platformTransactionManager.getTransaction(paramTransactionDefinition);

        if (account != null) {
            jdbcTemplate.update(UPDATE_ACCOUNT_ID, params);
            platformTransactionManager.commit(status);
            return true;
        } else {
            platformTransactionManager.rollback(status);
            return false;
        }
    }

    @Override
    public boolean transferBalance(int mainId, int id, int amount) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", id);
        params.addValue("balance", amount * -1);
        Account mainAccount = getAccountById(mainId).isPresent() ? getAccountById(mainId).get() : null;
        Account account = getAccountById(id).isPresent() ? getAccountById(id).get() : null;

        DefaultTransactionDefinition paramTransactionDefinition = new DefaultTransactionDefinition();
        TransactionStatus status = platformTransactionManager.getTransaction(paramTransactionDefinition);

        if (mainAccount != null && account != null) {
            try {
                jdbcTemplate.update(TRANSFER_BALANCE, params);
                params.addValue("id", mainId);
                params.addValue("balance", amount);
                jdbcTemplate.update(TRANSFER_BALANCE, params);
                platformTransactionManager.commit(status);
                return true;
            } catch (Exception e) {
                platformTransactionManager.rollback(status);
                //e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean updateAccountHistory(int id, long dt, int amount, int type, boolean result, long transactionId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("account_id", id);
        params.addValue("amount", amount);
        params.addValue("dt", new Date(dt));
        params.addValue("type", type);
        params.addValue("result", result);
        params.addValue("transaction_id", transactionId);
        DefaultTransactionDefinition paramTransactionDefinition = new DefaultTransactionDefinition();
        TransactionStatus status = platformTransactionManager.getTransaction(paramTransactionDefinition);
        int count = jdbcTemplate.update(UPDATE_ACCOUNT_HISTORY, params);
        platformTransactionManager.commit(status);
        return count > 0;
    }

    @Override
    public long getAllBalance() {
        return jdbcTemplate.query(GET_ALL_BALANCE, new BalanceMapper()).get(0);
    }

    @Override
    public int getCount() {
        Integer result = jdbcTemplate.queryForObject(SELECT_ACCOUNTS_COUNT, new MapSqlParameterSource(), Integer.class);
        return result != null ? result : 0;
    }
}
