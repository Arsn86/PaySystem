package ru.dz.pay.system.helpers.dbs;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import ru.dz.pay.system.helpers.data.DataSet;
import ru.dz.pay.system.helpers.hibernate.HibernateConfHelper;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

public class DBServiceHibernateImpl implements DBService {

    private final SessionFactory sessionFactory;

    private final String SELECT_ACCOUNTS_COUNT = "select count(*) from  AccountsDataSet accounts";

    public DBServiceHibernateImpl() {
        HibernateConfHelper helper = new HibernateConfHelper();
        sessionFactory = helper.createSessionFactory();
    }

    @Override
    public <T extends DataSet> void save(T account) throws SQLException, IllegalAccessException, NoSuchFieldException {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.save(account);
            session.getTransaction().commit();
        }
    }

    @Override
    public <T extends DataSet> T load(long id, Class<T> clazz) throws SQLException, IllegalAccessException, InstantiationException {
        try (Session session = sessionFactory.openSession()) {
            //session.beginTransaction();
            //T result = session.load(clazz, id);
            //session.getTransaction().commit();
            return session.load(clazz, id);
        }
    }

    @Override
    public <T extends DataSet> List<T> getAll(Class<T> clazz) throws SQLException, IllegalAccessException, InstantiationException {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<T> criteriaQuery = builder.createQuery(clazz);
            criteriaQuery.from(clazz);
            return session.createQuery(criteriaQuery).list();
        }
    }

    @Override
    public long getCount() throws SQLException, IllegalAccessException, InstantiationException {
        long count = 0;
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            Query query = session.createQuery(SELECT_ACCOUNTS_COUNT);

            for (Iterator iterator = query.iterate(); iterator.hasNext(); ) {
                count = (long) iterator.next();
            }

            session.getTransaction().commit();
        }
        return count;
    }

    @Override
    public void createTables() throws SQLException {
        //"hibernate.hbm2ddl.auto", "create"
    }

    @Override
    public void dropTables() throws SQLException {
        //"hibernate.hbm2ddl.auto", "create"
    }

    @Override
    public void close() throws Exception {
        sessionFactory.close();
    }
}
