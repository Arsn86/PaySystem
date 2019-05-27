package ru.dz.pay.system;

import ru.dz.pay.system.helpers.data.AccountsDataSet;
import ru.dz.pay.system.helpers.dbs.DBService;
import ru.dz.pay.system.helpers.dbs.DBServiceHibernateImpl;
import ru.dz.pay.system.helpers.dbs.DBServiceImpl;

import java.util.List;
import java.util.Random;

public class MainHibernate {
    public static void main(String[] args) {
        System.out.println("Start...");

        Random random = new Random();

        try (DBServiceImpl dbService = new DBServiceImpl()) {
            dbService.dropTables();
            dbService.createTables();

            dbService.createMainBalance(-1, 1000000);

        } catch (Exception e) {
            e.printStackTrace();
        }

        try (DBService dbService = new DBServiceHibernateImpl()) {

            AccountsDataSet acc1 = new AccountsDataSet(random.nextInt(99));
            AccountsDataSet acc2 = new AccountsDataSet(random.nextInt(99));
            AccountsDataSet acc3 = new AccountsDataSet(25);

            dbService.save(acc1);
            dbService.save(acc2);
            dbService.save(acc3);

            AccountsDataSet accFromDB = dbService.load(2, AccountsDataSet.class);

            System.out.println("Account 2 FromDB: " + accFromDB);

            for (int i = 4; i < 10; i++) {
                dbService.save(new AccountsDataSet(100));
            }

            List<AccountsDataSet> list = dbService.getAll(AccountsDataSet.class);

            list.forEach(System.out::println);

            System.out.println("Count: " + dbService.getCount());

        } catch (Exception e) {
            e.printStackTrace();
        }

        /*session.close();
        sessionFactory.close();*/

        System.out.println("End!");
    }
}
