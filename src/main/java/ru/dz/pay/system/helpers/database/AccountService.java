package ru.dz.pay.system.helpers.database;

import java.util.List;

public interface AccountService {
    Account getAccount(int id);

    boolean updateAccount(int id, int balance);

    boolean transferBalance(int mainId, int id, int amount);

    long getAllBalance();

    List<Account> getAllAccounts();

}
