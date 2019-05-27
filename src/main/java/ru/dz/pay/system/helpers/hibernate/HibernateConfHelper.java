package ru.dz.pay.system.helpers.hibernate;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import ru.dz.pay.system.helpers.data.AccountHistoryDataSet;
import ru.dz.pay.system.helpers.data.AccountsDataSet;
import ru.dz.pay.system.helpers.data.DataSet;
import ru.dz.pay.system.helpers.database.ConnectionSettings;


public class HibernateConfHelper {

    private Configuration configuration = new Configuration();
    private ConnectionSettings connectionSettings = new ConnectionSettings();

    private void getConfig() {
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL5Dialect");
        configuration.setProperty("hibernate.connection.driver_class", connectionSettings.getJdbcDriver());
        configuration.setProperty("hibernate.connection.url", connectionSettings.getJdbcString());
        configuration.setProperty("hibernate.connection.username", connectionSettings.getJdbcUser());
        configuration.setProperty("hibernate.connection.password", connectionSettings.getJdbcPassword());
        configuration.setProperty("hibernate.show_sql", "true");
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");
        configuration.setProperty("hibernate.connection.autocommit", "false");
        configuration.setProperty("hibernate.enable_lazy_load_no_trans", "true");
        configuration.addAnnotatedClass(DataSet.class);
        configuration.addAnnotatedClass(AccountsDataSet.class);
        configuration.addAnnotatedClass(AccountHistoryDataSet.class);
    }

    public SessionFactory createSessionFactory() {
        getConfig();
        StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder();
        builder.applySettings(configuration.getProperties());
        ServiceRegistry serviceRegistry = builder.build();
        return configuration.buildSessionFactory(serviceRegistry);
    }

}
