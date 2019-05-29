package ru.dz.pay.system.database;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.lang.String.format;

@Primary
@Service
public class AccountServiceImpl implements AccountService {

    private final AccountDao accountDao;

    @Autowired
    public AccountServiceImpl(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Override
    public Account getAccount(int id) {
        return accountDao.getAccountById(id).orElseThrow(() -> new AccountNotFoundException(format("Account with id = %s not found!", id)));
    }

    @Override
    public boolean updateAccount(int id, int balance) {
        return accountDao.updateAccountById(id, balance);
    }

    @Override
    public boolean transferBalance(int mainId, int id, int amount) {
        return accountDao.transferBalance(mainId, id, amount);
    }

    @Override
    public long getAllBalance() {
        return accountDao.getAllBalance();
    }

    @Override
    public boolean updateAccountHistory(int id, long dt, int amount, int type, boolean result, long transactionId) {
        return accountDao.updateAccountHistory(id, dt, amount, type, result, transactionId);
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountDao.getAllAccounts();
    }

    @Override
    public int getCount() {
        return accountDao.getCount();
    }

}
