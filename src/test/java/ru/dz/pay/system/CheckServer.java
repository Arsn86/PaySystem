package ru.dz.pay.system;

import com.google.gson.Gson;
import lombok.ToString;
import org.junit.Before;
import org.junit.Test;
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

public class CheckServer {

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

            System.out.println("Account 2 FromDB: " + accFromDB);

            for (int i = 4; i <= maxAccId; i++) {
                dbService.save(new AccountsDataSet(100));
            }

            List<AccountsDataSet> list = dbService.getAll(AccountsDataSet.class);

            list.forEach(System.out::println);

            System.out.println("Count: " + dbService.getCount());

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
        //init();
    }

    @Test
    public void shutdownServerManager() throws IOException {
        TransactionRequest request = new TransactionRequest();
        HttpResp resp = client.send(MessageType.GET, "payment/stop", request);
        assertTrue(resp.isOk());
        assertEquals("OK, Manager stopped!", resp.getBody());
    }

    @Test
    public void checkServerStatus() throws IOException {
        if (!init) init();
        TransactionRequest request = new TransactionRequest();
        HttpResp resp = client.send(MessageType.GET, "payment", request);
        assertTrue(resp.isOk());
        assertEquals("ALIVE", resp.getBody());
    }

    @Test
    public void checkPost() throws IOException {

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

    @Test
    public void checkPostLoad() {

        long start = System.currentTimeMillis();
        int loopCount = 10000;
        int startIndex = 5;
        Random random = new Random();
        String strategyName = "payment/pay";

        FileManager manager = FileManager.getInstance();
        manager.init("load");

        for (int i = 0; i < loopCount; i++) {
            map.put(getNextTransactionId(transactionIndexDiff), new Counter());
        }

        Runnable runnable = () -> {
            while (index.get() < loopCount + startIndex) {

                System.out.println(format("Index: %s, count: %s", index.get(), loopCount + startIndex));

                try {
                    TransactionRequest request = getNewRequest(randomAccountId ? random.nextInt(maxAccId) + 1 : 5, 0);
                    long id = request.getTransactionId();
                    System.out.println("request: " + request);
                    map.get(id).out.incrementAndGet();
                    HttpResp resp = client.send(MessageType.POST, strategyName, request);
                    TransactionResponse response;
                    assertNotNull(resp.getBody());
                    manager.writeString("request: " + request + " -> " + resp);
                    if (resp.getBody() != null && resp.getBody().length() > 0) {
                        response = new Gson().fromJson(resp.getBody(), TransactionResponse.class);
                        manager.writeString("request: " + request + " -> " + response);
                        Counter counter = map.get(response.getTransactionId());
                        counter.in.incrementAndGet();
                        counter.time = response.getDateTime() - request.getDateTime();
                        assertEquals(id, response.getTransactionId());
                        assertTrue(response.isResult());
                    }
                    assertTrue(resp.isOk());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        long end = System.currentTimeMillis() - start;
        System.out.println("Map initialized! Time: " + end);

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
                System.out.println(format("Err #%s | Check: k = %s : v = %s, Latency: %s | AssertionError: %s", errCount.incrementAndGet(), k, v, v.time, e));
                e.printStackTrace();
            }
            if (max.get() < v.time) max.set(v.time);
        });

        System.out.println(format("Load complete! Time: %s, (max latency: %s)| Count: %s (TPS=%s) | Errors = %s", end, max.get(), loopCount, loopCount * 1000 / end, errCount.get()));

        assertEquals(0, errCount.get());

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
    public void setBalance() throws IOException {

        init();

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

            System.out.println("Count: " + dbService.getCount());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void showBalance() {
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
    public void transferBalance() throws IOException {
        init();
        long startBalance = checkBalance();
        System.out.println("Initial balance = " + startBalance);

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
        System.out.println("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
    }

    private long getNextTransactionId() {
        return index.incrementAndGet();
    }

    private long getNextTransactionId(int diff) {
        return diff + index.incrementAndGet();
    }

    @Test
    public void transferBalanceFast() throws IOException, InterruptedException {

        shutdownServerManager();

        init();
        checkServerStatus();

        Thread.sleep(1600);

        long startBalance = checkBalance();
        System.out.println("Initial balance = " + startBalance);

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
        System.out.println("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
        assertEquals(startBalance, endBalance);
    }

    @Test
    public void transferBalanceLoadPay() throws InterruptedException {
        transferBalanceLoad("payment/pay", 10000, false);
    }

    @Test
    public void transferBalanceLoadWdb() throws InterruptedException {
        transferBalanceLoad("payment/wdb", 10000, false);
    }

    @Test
    public void transferBalanceLoadFast() throws InterruptedException {
        transferBalanceLoad("payment/fast", 10000, false);
    }

    @Test
    public void transferBalanceLoadTest() throws IOException, InterruptedException {
        randomAccountId = false;
        transferBalanceAll();
    }

    private void transferBalanceAll() throws IOException, InterruptedException {
        init = true;

        checkServerStatus();

        int loopCount = 10000;

        long startBalance = checkBalance();
        System.out.println("Initial balance = " + startBalance);

        String pay = transferBalanceLoad("payment/pay", loopCount, true);

        index.set(5);
        map.clear();
        client.init();
        checkServerStatus();
        String wdb = transferBalanceLoad("payment/wdb", loopCount, true);

        index.set(5);
        map.clear();
        client.init();
        checkServerStatus();
        String fast = transferBalanceLoad("payment/fast", loopCount, true);

        long endBalance = checkBalance();
        System.out.println("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
        System.out.println("============================================================================================\nPAY:\n" + pay + "\nWDB:\n" + wdb + "\nFAST:\n" + fast);
        assertEquals(startBalance, endBalance);
    }

    @Test
    public void transferBalanceLoadTestRandom() throws IOException, InterruptedException {
        randomAccountId = true;
        transferBalanceAll();
    }

    private String transferBalanceLoad(String strategyName, int loopCount, boolean allow) throws InterruptedException {

        Thread.sleep(1600);

        long start = System.currentTimeMillis();
        int startIndex = 5;
        Random random = new Random();

        long startBalance = checkBalance();
        System.out.println("Initial balance = " + startBalance);

        for (int i = 0; i < loopCount; i++) {
            map.put(getNextTransactionId(transactionIndexDiff), new Counter());
        }

        FileManager manager = FileManager.getInstance();
        manager.init(strategyName.substring(strategyName.length() - 3));

        /*System.out.println("MAP:");
        map.forEach((k, v) -> {
            System.out.println(k);
        });*/

        Runnable runnable = () -> {

            while (index.get() < loopCount + startIndex) {

                System.out.println(format("Index: %s, count: %s", index.get(), loopCount + startIndex));

                long id = 0;
                HttpResp resp = null;
                TransactionRequest request = null;

                try {
                    request = getNewRequest(randomAccountId ? random.nextInt(maxAccId) + 1 : 5, random.nextInt(50), random.nextInt(10) < 5 ? 12 : 14);
                    id = request.getTransactionId();
                    System.out.println("request: " + request);
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
        System.out.println("Map initialized! Time: " + end);

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

                System.out.println(s);
                e.printStackTrace();
            }
            if (max.get() < v.time) max.set(v.time);
        });

        Thread.sleep(1600);

        long endBalance = checkBalance();
        System.out.println("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
        boolean okBalance = endBalance == startBalance;
        String result = format("Load complete! Time: %s, (max latency: %s)| Count: %s (TPS=%s) | Balance is: %s | Errors = %s", end, max.get(), loopCount, loopCount * 1000 / end, okBalance, errCount.get());
        assertEquals(startBalance, endBalance);

        System.out.println(result);

        if (!allow) assertEquals(0, errCount.get());

        return result;

    }

}
