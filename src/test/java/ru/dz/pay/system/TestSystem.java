package ru.dz.pay.system;

import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.dz.pay.system.database.Account;
import ru.dz.pay.system.database.AccountService;
import ru.dz.pay.system.database.AccountServiceImpl;
import ru.dz.pay.system.helpers.FileManager;

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
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Application.class, AccountServiceImpl.class})
@EnableConfigurationProperties
public class TestSystem {

    private static final Logger log = LogManager.getLogger(TestSystem.class);

    private AccountService service;

    private static final String url = "http://localhost:8080/payment";
    private static final RestTemplate restTemplate = new RestTemplate();

    private static final int transactionIndexDiff = 888;
    private static final int threadPoolSize = 10;
    private static final long mainBalance = 1000000;
    private static final int maxAccId = 10;
    private static final int maxLatency = 4000;
    private static boolean randomAccountId = false;

    @ToString
    class Counter {
        AtomicInteger out = new AtomicInteger(0);
        AtomicInteger in = new AtomicInteger(0);
        long time = 0;
    }

    private static final AtomicLong index = new AtomicLong(5);
    private static final Map<Long, Counter> map = new ConcurrentHashMap<>();

    @Autowired
    public void setService(AccountService service) {
        this.service = service;
    }

    private long getNextTransactionId() {
        return index.incrementAndGet();
    }

    private long getNextTransactionId(int diff) {
        return diff + index.incrementAndGet();
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

    @SuppressWarnings("ConstantConditions")
    @Before
    public void before() {
        assertTrue(maxAccId > 4);
        index.set(5);
        map.clear();
    }

    @Test
    public void shutdownServerManager() {

        ResponseEntity<String> response = restTemplate.getForEntity(url + "/stop", String.class);
        log.info("Response: " + response);
        assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
        assertEquals("OK, Manager stopped!", response.getBody());
    }

    @Test
    public void checkServerStatus() {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        log.info("Response: " + response);
        assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
        assertEquals("ALIVE", response.getBody());
    }

    @Test
    public void checkPost() {
        long transactionId = 888;
        TransactionRequest request = new TransactionRequest();

        request.setTransactionId(transactionId);
        request.setAccountId(1);

        TransactionResponse response = restTemplate.postForObject(url + "/pay", request, TransactionResponse.class);
        assertNotNull(response);
        log.info("Response: " + response);
        assertEquals(transactionId, response.getTransactionId());
        assertFalse(response.isResult());
    }

    @Test
    public void checkPostLoad() {

        long start = System.currentTimeMillis();
        int loopCount = 10000;
        int startIndex = 5;
        Random random = new Random();
        String strategyName = "/pay";

        FileManager manager = FileManager.getInstance();
        manager.init("load");

        for (int i = 0; i < loopCount; i++) {
            map.put(getNextTransactionId(transactionIndexDiff), new Counter());
        }

        Runnable runnable = () -> {
            while (index.get() < loopCount + startIndex) {

                log.info(format("Index: %s, count: %s", index.get(), loopCount + startIndex));

                TransactionRequest request = getNewRequest(randomAccountId ? random.nextInt(maxAccId) + 1 : 5, random.nextInt(1000));
                long id = request.getTransactionId();
                log.info("request: " + request);
                map.get(id).out.incrementAndGet();
                ResponseEntity<TransactionResponse> responseEntity = restTemplate.postForEntity(url + strategyName, request, TransactionResponse.class);
                log.info("Response: " + responseEntity);
                assertThat(responseEntity.getStatusCode(), equalTo(HttpStatus.OK));
                TransactionResponse response = responseEntity.getBody();
                assertNotNull(response);
                manager.writeString("request: " + request + " -> " + response);
                Counter counter = map.get(response.getTransactionId());
                counter.in.incrementAndGet();
                counter.time = response.getDateTime() - request.getDateTime();
                assertEquals(id, response.getTransactionId());
                assertTrue(response.isResult());
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
                log.info(format("Err #%s | Check: k = %s : v = %s, Latency: %s | AssertionError: %s", errCount.incrementAndGet(), k, v, v.time, e));
                e.printStackTrace();
            }
            if (max.get() < v.time) max.set(v.time);
        });

        log.info(format("Load complete! Time: %s, (max latency: %s)| Count: %s (TPS=%s) | Errors = %s", end, max.get(), loopCount, loopCount * 1000 / end, errCount.get()));

        assertEquals(0, errCount.get());
    }

    @Test
    public void setBalance() {
        String strategyName = "/pay";

        for (int i = 1; i <= 2; i++) {

            TransactionRequest request = getNewRequest(i, 100 * i);
            TransactionResponse response = restTemplate.postForObject(url + strategyName, request, TransactionResponse.class);
            assertNotNull(response);
            assertEquals(request.getTransactionId(), response.getTransactionId());
            assertTrue(response.isResult());
        }

        TransactionRequest request = getNewRequest(50000, 500);
        TransactionResponse response = restTemplate.postForObject(url + strategyName, request, TransactionResponse.class);
        assertNotNull(response);
        assertEquals(request.getTransactionId(), response.getTransactionId());
        assertFalse(response.isResult());

        service.updateAccount(-1, 1000000);
        service.updateAccount(1, 100);
        service.updateAccount(2, 200);
        service.updateAccount(3, 25);

        List<Account> list = service.getAllAccounts();
        log.info(list);
        log.info("Count: " + service.getCount());
    }

    @Test
    public void showBalance() {
        setBalance();
        long result = checkBalance();
        log.info("Balance: " + result);
        assertTrue(result > mainBalance);
    }

    private long checkBalance() {
        return service.getAllBalance();
    }

    @Test
    public void transferBalanceStandard() {
        transferBalance("/pay");
    }

    @Test
    public void transferBalanceFast() throws InterruptedException {
        shutdownServerManager();
        setBalance();
        Thread.sleep(600);
        checkServerStatus();
        transferBalance("/fast");
    }


    public void transferBalance(String strategyName) {

        setBalance();

        long startBalance = checkBalance();
        log.info("Initial balance = " + startBalance);

        TransactionRequest request = getNewRequest(3, 20);
        request.setType(12);
        TransactionResponse response = restTemplate.postForObject(url + strategyName, request, TransactionResponse.class);
        log.info("Request: " + request + " -> Response: " + response);
        assertNotNull(response);
        assertEquals(request.getTransactionId(), response.getTransactionId());
        assertTrue(response.isResult());

        request = getNewRequest(3, 20);
        request.setType(12);
        response = restTemplate.postForObject(url + strategyName, request, TransactionResponse.class);
        log.info("Request: " + request + " -> Response: " + response);
        assertNotNull(response);
        assertEquals(request.getTransactionId(), response.getTransactionId());
        assertFalse(response.isResult());

        request = getNewRequest(3, 5);
        request.setType(12);
        response = restTemplate.postForObject(url + strategyName, request, TransactionResponse.class);
        log.info("Request: " + request + " -> Response: " + response);
        assertNotNull(response);
        assertEquals(request.getTransactionId(), response.getTransactionId());
        assertTrue(response.isResult());

        request = getNewRequest(5, 50);
        request.setType(14);
        response = restTemplate.postForObject(url + strategyName, request, TransactionResponse.class);
        log.info("Request: " + request + " -> Response: " + response);
        assertNotNull(response);
        assertEquals(request.getTransactionId(), response.getTransactionId());
        assertTrue(response.isResult());

        long endBalance = checkBalance();
        assertEquals(startBalance, endBalance);
        log.info("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
    }

    @Test
    public void setServerStatusSync() {
        ResponseEntity<String> response = restTemplate.getForEntity(url + "/sync", String.class);
        log.info("Response: " + response);
        assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
        assertEquals("OK, Manager sync state!", response.getBody());
    }

    @Test
    public void setServerStatusAsync() {
        ResponseEntity<String> response = restTemplate.getForEntity(url + "/async", String.class);
        log.info("Response: " + response);
        assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
        assertEquals("OK, Manager async state!", response.getBody());
    }

    @Test
    public void transferBalanceLoadPay() throws InterruptedException {
        transferBalanceLoad("/pay", 10000, false);
    }

    @Test
    public void transferBalanceLoadWdb() throws InterruptedException {
        transferBalanceLoad("/wdb", 10000, false);
    }

    @Test
    public void transferBalanceLoadFast() throws InterruptedException {
        transferBalanceLoad("/fast", 10000, false);
    }

    @Test
    public void transferBalanceLoadDbt() throws InterruptedException {
        setServerStatusSync();
        transferBalanceLoad("/dbt", 10000, false);
        setServerStatusAsync();
    }

    @Test
    public void transferBalanceLoadTest() throws InterruptedException {
        randomAccountId = false;
        transferBalanceAll();
    }

    private void transferBalanceAll() throws InterruptedException {
        checkServerStatus();

        int loopCount = 10000;

        long startBalance = checkBalance();
        log.info("Initial balance = " + startBalance);

        String pay = transferBalanceLoad("/pay", loopCount, true);

        index.set(5);
        map.clear();
        checkServerStatus();
        String wdb = transferBalanceLoad("/wdb", loopCount, true);

        index.set(5);
        map.clear();
        shutdownServerManager();
        checkServerStatus();
        String fast = transferBalanceLoad("/fast", loopCount, true);

        index.set(5);
        map.clear();
        shutdownServerManager();
        checkServerStatus();
        setServerStatusSync();
        String dbt = transferBalanceLoad("/dbt", loopCount, true);
        setServerStatusAsync();

        long endBalance = checkBalance();
        log.info("End balance = " + endBalance + ", startBalance = " + startBalance + " | Diff: " + (endBalance - startBalance));
        log.info("============================================================================================\n" +
                "PAY:\n" + pay + "\nWDB:\n" + wdb + "\nFAST:\n" + fast + "\nDBT:\n" + dbt);
        assertEquals(startBalance, endBalance);
    }

    @Test
    public void transferBalanceLoadTestRandom() throws InterruptedException {
        randomAccountId = true;
        transferBalanceAll();
    }

    private String transferBalanceLoad(String strategyName, int loopCount, boolean allow) throws InterruptedException {

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

        Runnable runnable = () -> {

            while (index.get() < loopCount + startIndex) {

                log.info(format("Index: %s, count: %s", index.get(), loopCount + startIndex));

                long id = 0;
                ResponseEntity<TransactionResponse> resp = null;
                TransactionRequest request = null;

                try {
                    request = getNewRequest(randomAccountId ? random.nextInt(maxAccId) + 1 : 5, random.nextInt(50), random.nextInt(10) < 5 ? 12 : 14);
                    id = request.getTransactionId();
                    log.info("request: " + request);
                    map.get(id).out.incrementAndGet();
                    manager.writeString("request: " + request + " -> set out: " + map.get(id).out.get());
                    resp = restTemplate.postForEntity(url + strategyName, request, TransactionResponse.class);
                    assertNotNull(resp.getBody());
                    manager.writeString("request: " + request + " -> " + resp);
                    TransactionResponse response = resp.getBody();
                    if (resp.getBody() != null) {
                        manager.writeString("request: " + request + " -> " + response);
                        Counter counter = map.get(response.getTransactionId());
                        counter.in.incrementAndGet();
                        counter.time = response.getDateTime() - request.getDateTime();
                        assertEquals(id, response.getTransactionId());
                    }
                    assertThat(resp.getStatusCode(), equalTo(HttpStatus.OK));
                } catch (RestClientException e) {
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
                log.error(s);
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

        return result;

    }

}
