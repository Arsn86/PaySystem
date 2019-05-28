package ru.dz.pay.system;

import com.google.gson.Gson;
import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.dz.pay.system.helpers.FileManager;
import ru.dz.pay.system.helpers.data.AccountsDataSet;
import ru.dz.pay.system.helpers.dbs.DBService;
import ru.dz.pay.system.helpers.dbs.DBServiceHibernateImpl;
import ru.dz.pay.system.helpers.dbs.DBServiceImpl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CheckServer {

    private static final Logger log = LogManager.getLogger(CheckServer.class);

    private Client client = new Client();
    private static final long mainBalance = 1000000;
    private static final int maxAccId = 10;
    private static final int threadPoolSize = 10;
    private static final int maxLatency = 2000;
    private static boolean randomAccountId = false;

    private static final int transactionIndexDiff = 888;

    @ToString
    class Counter {
        AtomicInteger out = new AtomicInteger(0);
        AtomicInteger in = new AtomicInteger(0);
        long time = 0;
    }

    private static final AtomicLong index = new AtomicLong(5);
    private static final Map<Long, Counter> map = new ConcurrentHashMap<>();

    private boolean init = false;

    private class Result {
        String msg;
        long err;

        Result(String msg, long err) {
            this.msg = msg;
            this.err = err;
        }
    }

    private void init() {
        Random random = new Random();

        try (DBServiceImpl dbService = new DBServiceImpl()) {
            dbService.dropTables();
            dbService.createTables();

            dbService.createMainBalance(-1, mainBalance);

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

            log.info("Account 2 FromDB: " + accFromDB);

            for (int i = 4; i <= maxAccId; i++) {
                dbService.save(new AccountsDataSet(100));
            }

            List<AccountsDataSet> list = dbService.getAll(AccountsDataSet.class);

            list.forEach(System.out::println);

            log.info("Count: " + dbService.getCount());

            init = true;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Before
    public void before() {
        assertTrue(maxAccId > 4);
        index.set(5);
        map.clear();
        client.init();
    }

    @Test
    public void t01_shutdownServerManager() throws IOException {
        TransactionRequest request = new TransactionRequest();
        HttpResp resp = client.send(MessageType.GET, "payment/stop", request);
        assertTrue(resp.isOk());
        assertEquals("OK, Manager stopped!", resp.getBody());
    }

    @Test
    public void t02_checkServerStatus() throws IOException {
        if (!init) init();
        TransactionRequest request = new TransactionRequest();
        HttpResp resp = client.send(MessageType.GET, "payment", request);
        assertTrue(resp.isOk());
        assertEquals("ALIVE", resp.getBody());
    }

    @Test
    public void t03_checkPost() throws IOException {

        long transactionId = 888;
        TransactionRequest request = new TransactionRequest();

        request.setTransactionId(transactionId);
        request.setAccountId(1);

        TransactionResponse response;

        HttpResp resp = client.send(MessageType.POST, "payment/pay", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertEquals(transactionId, response.getTransactionId());
            assertFalse(response.isResult());
        }
        assertTrue(resp.isOk());
    }

    private TransactionRequest getNewRequest(int id, int amount) {
        return getNewRequest(id, amount, 0);
    }

    private TransactionRequest getNewRequest(int id, int amount, int type) {
        long transactionId = getNextTransactionId(transactionIndexDiff);
        TransactionRequest request = new TransactionRequest();

        request.setTransactionId(transactionId);
        request.setAccountId(id);
        if (type == 12 || type == 14) {
            request.setType(type);
        } else {
            request.setType(10);
        }
        request.setAmount(amount);
        return request;
    }

    @Test
    public void t04_setBalance() throws IOException {

        t01_shutdownServerManager();

        init();

        t02_checkServerStatus();

        for (int i = 1; i <= 2; i++) {

            TransactionRequest request = getNewRequest(i, 100 * i);
            TransactionResponse response;

            HttpResp resp = client.send(MessageType.POST, "payment/pay", request);
            if (resp.getBody() != null && resp.getBody().length() > 0) {
                response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
                assertEquals(request.getTransactionId(), response.getTransactionId());
                assertTrue(response.isResult());
            }
            assertTrue(resp.isOk());
        }

        TransactionRequest request = getNewRequest(50000, 500);
        TransactionResponse response;
        HttpResp resp = client.send(MessageType.POST, "payment/pay", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertFalse(response.isResult());
        }
        assertTrue(resp.isOk());

        try (DBService dbService = new DBServiceHibernateImpl()) {

            AccountsDataSet acc1 = dbService.load(1, AccountsDataSet.class);
            AccountsDataSet acc2 = dbService.load(2, AccountsDataSet.class);
            AccountsDataSet acc3 = dbService.load(3, AccountsDataSet.class);

            assertEquals(100, acc1.getBalance());
            assertEquals(200, acc2.getBalance());
            assertEquals(25, acc3.getBalance());

            List<AccountsDataSet> list = dbService.getAll(AccountsDataSet.class);

            list.forEach(System.out::println);

            log.info("Count: " + dbService.getCount());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void t05_showBalance() {
        assertTrue(checkBalance() > mainBalance);
    }

    private long checkBalance() {
        try (DBService dbService = new DBServiceHibernateImpl()) {
            List<AccountsDataSet> list = dbService.getAll(AccountsDataSet.class);
            return list.stream().mapToLong(AccountsDataSet::getBalance).sum();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Test
    public void t06_transferBalance() throws IOException {
        //init();
        long startBalance = checkBalance();
        log.info("Initial balance = " + startBalance);

        TransactionRequest request = getNewRequest(3, 20);
        request.setType(12);
        TransactionResponse response;
        HttpResp resp = client.send(MessageType.POST, "payment/pay", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertEquals(request.getTransactionId(), response.getTransactionId());
            assertTrue(response.isResult());
        }
        assertTrue(resp.isOk());

        request = getNewRequest(3, 20);
        request.setType(12);
        resp = client.send(MessageType.POST, "payment/pay", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertEquals(request.getTransactionId(), response.getTransactionId());
            assertFalse(response.isResult());
        }
        assertTrue(resp.isOk());

        request = getNewRequest(3, 5);
        request.setType(12);
        resp = client.send(MessageType.POST, "payment/pay", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertEquals(request.getTransactionId(), response.getTransactionId());
            assertTrue(response.isResult());
        }
        assertTrue(resp.isOk());

        request = getNewRequest(5, 50);
        request.setType(14);
        resp = client.send(MessageType.POST, "payment/pay", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertEquals(request.getTransactionId(), response.getTransactionId());
            assertTrue(response.isResult());
        }
        assertTrue(resp.isOk());

        long endBalance = checkBalance();
        assertEquals(startBalance, endBalance);
        log.info("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
    }

    private long getNextTransactionId() {
        return index.incrementAndGet();
    }

    private long getNextTransactionId(int diff) {
        return diff + index.incrementAndGet();
    }

    @Test
    public void t07_transferBalanceFast() throws IOException, InterruptedException {

        t01_shutdownServerManager();
        t02_checkServerStatus();

        Thread.sleep(1600);

        long startBalance = checkBalance();
        log.info("Initial balance = " + startBalance);

        TransactionRequest request = getNewRequest(3, 20);
        request.setType(12);
        TransactionResponse response;
        HttpResp resp = client.send(MessageType.POST, "payment/fast", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertEquals(request.getTransactionId(), response.getTransactionId());
            assertTrue(response.isResult());
        }
        assertTrue(resp.isOk());

        request = getNewRequest(3, 20);
        request.setType(12);
        resp = client.send(MessageType.POST, "payment/fast", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertEquals(request.getTransactionId(), response.getTransactionId());
            assertFalse(response.isResult());
        }
        assertTrue(resp.isOk());

        request = getNewRequest(3, 5);
        request.setType(12);
        resp = client.send(MessageType.POST, "payment/fast", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertEquals(request.getTransactionId(), response.getTransactionId());
            assertTrue(response.isResult());
        }
        assertTrue(resp.isOk());

        request = getNewRequest(5, 50);
        request.setType(14);
        resp = client.send(MessageType.POST, "payment/fast", request);
        if (resp.getBody() != null && resp.getBody().length() > 0) {
            response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
            assertEquals(request.getTransactionId(), response.getTransactionId());
            assertTrue(response.isResult());
        }
        assertTrue(resp.isOk());

        Thread.sleep(1600);

        long endBalance = checkBalance();
        log.info("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
        assertEquals(startBalance, endBalance);
    }

    @Test
    public void t08_setServerStatusSync() throws IOException {
        TransactionRequest request = new TransactionRequest();
        HttpResp resp = client.send(MessageType.GET, "payment/sync", request);
        assertTrue(resp.isOk());
        assertEquals("OK, Manager sync state!", resp.getBody());
    }

    @Test
    public void t09_setServerStatusAsync() throws IOException {
        TransactionRequest request = new TransactionRequest();
        HttpResp resp = client.send(MessageType.GET, "payment/async", request);
        assertTrue(resp.isOk());
        assertEquals("OK, Manager async state!", resp.getBody());
    }

    @Test
    public void t10_transferBalanceLoadPay() throws InterruptedException {
        Result result = transferBalanceLoad("payment/pay", 10000, false);
        assertEquals(0, result.err);
    }

    @Test
    public void t11_transferBalanceLoadWdb() throws InterruptedException {
        Result result = transferBalanceLoad("payment/wdb", 10000, false);
        assertEquals(0, result.err);
    }

    @Test
    public void t12_transferBalanceLoadFast() throws InterruptedException {
        Result result = transferBalanceLoad("payment/fast", 10000, false);
        assertEquals(0, result.err);
    }

    @Test
    public void t13_transferBalanceLoadDbt() throws InterruptedException, IOException {
        t08_setServerStatusSync();
        Result result = transferBalanceLoad("payment/dbt", 10000, false);
        assertEquals(0, result.err);
        t09_setServerStatusAsync();
    }

    @Test
    public void t15_transferBalanceLoadTest() throws IOException, InterruptedException {
        randomAccountId = false;
        transferBalanceAll();
    }

    private void transferBalanceAll() throws IOException, InterruptedException {
        init = true;

        t02_checkServerStatus();

        int loopCount = 10000;

        long startBalance = checkBalance();
        log.info("Initial balance = " + startBalance);

        Result pay = transferBalanceLoad("payment/pay", loopCount, true);

        index.set(5);
        map.clear();
        client.init();
        t02_checkServerStatus();
        Result wdb = transferBalanceLoad("payment/wdb", loopCount, true);

        index.set(5);
        map.clear();
        client.init();
        t01_shutdownServerManager();
        t02_checkServerStatus();
        Result fast = transferBalanceLoad("payment/fast", loopCount, true);

        index.set(5);
        map.clear();
        client.init();
        t01_shutdownServerManager();
        t02_checkServerStatus();
        t08_setServerStatusSync();
        Result dbt = transferBalanceLoad("payment/dbt", loopCount, true);
        t09_setServerStatusAsync();

        long endBalance = checkBalance();
        log.info("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
        log.info("============================================================================================\n" +
                "PAY:\n" + pay.msg + "\nWDB:\n" + wdb.msg + "\nFAST:\n" + fast.msg + "\nDBT:\n" + dbt.msg);
        assertEquals(startBalance, endBalance);
        assertEquals(0, pay.err);
        assertEquals(0, wdb.err);
        assertEquals(0, fast.err);
        assertEquals(0, dbt.err);
    }

    @Test
    public void t16_transferBalanceLoadTestRandom() throws IOException, InterruptedException {
        randomAccountId = true;
        transferBalanceAll();
    }

    private Result transferBalanceLoad(String strategyName, int loopCount, boolean allow) throws InterruptedException {

        Thread.sleep(1600);

        long start = System.currentTimeMillis();
        int startIndex = 5;
        Random random = new Random();

        long startBalance = checkBalance();
        log.info("Initial balance = " + startBalance);

        for (int i = 0; i < loopCount; i++) {
            map.put(getNextTransactionId(transactionIndexDiff), new Counter());
        }

        FileManager manager = FileManager.getInstance();
        manager.init(strategyName.substring(strategyName.length() - 3));

        /*log.info("MAP:");
        map.forEach((k, v) -> {
            log.info(k);
        });*/

        Runnable runnable = () -> {

            while (index.get() < loopCount + startIndex) {

                log.info(format("Index: %s, count: %s", index.get(), loopCount + startIndex));

                long id = 0;
                HttpResp resp = null;
                TransactionRequest request = null;

                try {
                    request = getNewRequest(randomAccountId ? random.nextInt(maxAccId) + 1 : 5, random.nextInt(50), random.nextInt(10) < 5 ? 12 : 14);
                    id = request.getTransactionId();
                    log.info("request: " + request);
                    map.get(id).out.incrementAndGet();
                    manager.writeString("request: " + request + " -> set out: " + map.get(id).out.get());
                    resp = client.send(MessageType.POST, strategyName, request);
                    assertNotNull(resp.getBody());
                    manager.writeString("request: " + request + " -> " + resp);
                    if (resp.getBody() != null && resp.getBody().length() > 0) {
                        TransactionResponse response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
                        manager.writeString("request: " + request + " -> " + response);
                        Counter counter = map.get(response.getTransactionId());
                        counter.in.incrementAndGet();
                        counter.time = response.getDateTime() - request.getDateTime();
                        assertEquals(id, response.getTransactionId());
                        //assertFalse(response.isResult());
                    }
                    assertTrue(resp.isOk());
                } catch (IOException e) {
                    manager.writeString("ERROR Send! Id: " + id + " | Request: " + request + " -> " + resp + " | Exception: " + e);
                    e.printStackTrace();
                }
            }
        };

        long end = System.currentTimeMillis() - start;
        log.info("Map initialized! Time: " + end);

        index.set(startIndex);

        ExecutorService service = Executors.newFixedThreadPool(threadPoolSize);

        for (int i = 0; i < threadPoolSize; i++) {
            service.submit(runnable);
        }

        try {
            service.shutdown();
            service.awaitTermination(10, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (!service.isTerminated()) {
            Thread.yield();
        }

        end = System.currentTimeMillis() - start - end;

        manager.close();

        AtomicLong max = new AtomicLong(0);

        AtomicLong errCount = new AtomicLong();

        map.forEach((k, v) -> {
            try {
                assertEquals(1, v.in.intValue());
                assertEquals(1, v.out.intValue());
                assertTrue(v.time < maxLatency);
            } catch (AssertionError e) {
                String s = format("Err #%s | Check: k = %s : v = %s, Latency: %s | AssertionError: %s", errCount.incrementAndGet(), k, v, v.time, e);

                log.info(s);
                e.printStackTrace();
            }
            if (max.get() < v.time) max.set(v.time);
        });

        Thread.sleep(1600);

        long endBalance = checkBalance();
        log.info("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
        boolean okBalance = endBalance == startBalance;
        String result = format("Load complete! Time: %s, (max latency: %s)| Count: %s (TPS=%s) | Balance is: %s | Errors = %s", end, max.get(), loopCount, loopCount * 1000 / end, okBalance, errCount.get());
        assertEquals(startBalance, endBalance);

        log.info(result);

        if (!allow) assertEquals(0, errCount.get());

        return new Result(result, errCount.get());

    }

}
