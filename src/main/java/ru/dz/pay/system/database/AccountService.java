package ru.dz.pay.system.database;

import java.util.List;

public interface AccountService {
    Account getAccount(int id);

    boolean updateAccount(int id, int balance);

    boolean transferBalance(int mainId, int id, int amount);

    long getAllBalance();

    boolean updateAccountHistory(int id, long dt, int amount, int type, boolean result, long transactionId);

    List<Account> getAllAccounts();

    int getCount();

}
