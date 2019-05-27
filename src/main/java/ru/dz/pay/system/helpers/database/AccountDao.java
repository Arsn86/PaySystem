package ru.dz.pay.system.helpers.database;

import ru.dz.pay.system.helpers.Trw;

import java.util.List;
import java.util.Optional;

public interface AccountDao {
    boolean commit();

    Optional<Account> getAccountById(int id);

    List<Account> getAllAccounts();

    boolean updateAccountById(int id, int balance);

    boolean transferBalance(int mainId, int id, int amount);

    boolean updateAccountHistory(int id, long dt, int amount, int type, boolean result, long transactionId);

    int updateAccountHistory(List<Trw> list);

    long getAllBalance();
}
