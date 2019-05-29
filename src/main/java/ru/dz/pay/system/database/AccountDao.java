package ru.dz.pay.system.database;

import java.util.List;
import java.util.Optional;

public interface AccountDao {
    boolean commit();

    Optional<Account> getAccountById(int id);

    List<Account> getAllAccounts();

    boolean updateAccountById(int id, int balance);

    boolean transferBalance(int mainId, int id, int amount);

    boolean updateAccountHistory(int id, long dt, int amount, int type, boolean result, long transactionId);

    long getAllBalance();

    int getCount();

}
