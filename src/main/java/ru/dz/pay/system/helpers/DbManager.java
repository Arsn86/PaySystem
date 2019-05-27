package ru.dz.pay.system.helpers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.dz.pay.system.TransactionRequest;
import ru.dz.pay.system.helpers.database.Account;
import ru.dz.pay.system.helpers.database.AccountService;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;

public class DbManager {

    private static final Logger log = LogManager.getLogger(DbManager.class);

    private static final SimpleDateFormat fileDateFormat = new SimpleDateFormat("dd.MM.yyyy_HHmmssSSS");
    private static final ConcurrentLinkedQueue<Trw> queue = new ConcurrentLinkedQueue<>();

    private static String fileName = "data/transaction_" + fileDateFormat.format(new Date()) + ".log";

    private AccountService service;
    private static FileWriter writer;
    private static boolean init = false;
    private boolean threadDisable = false;
    private boolean sync = false;

    private static final DbManager instance = new DbManager();

    private static Map<Integer, Integer> map = new ConcurrentHashMap<>();
    private static final AtomicBoolean state = new AtomicBoolean(true);
    private static final AtomicBoolean change = new AtomicBoolean(false);

    private static final long interval = 100;

    private final Thread thread = new Thread(() -> {
        try {
            while (init) {
                Thread.sleep(interval);
                if (service != null && !threadDisable && change.get()) {
                    boolean result = sync ? saveMapToDbSync() : saveMapToDb();
                    log.info("Save map to db complete! Sync = " + sync + " Result: " + result);
                    writeString("Save map to db complete! Result: " + result);
                }
            }
        } catch (InterruptedException e) {
            shutdown();
        }
    });

    private final Thread qThread = new Thread(() -> {
        try {
            while (init) {
                Thread.sleep(interval);
                if (service != null && !threadDisable && queue.size() > 0) {
                    boolean result;
                    int count = 0;
                    List<Trw> list = new ArrayList<>();
                    try {
                        for (int i = 0; i < 1000; i++) {
                            Trw req = queue.poll();
                            if (req == null || threadDisable) break;
                            list.add(req);
                            //TransactionRequest request = req.request;
                            //result = result & service.updateAccountHistory(request.getAccountId(), request.getDateTime(), request.getAmount(), request.getType(), req.result, request.getTransactionId());
                        }
                        count = service.updateAccountHistory(list);
                        result = count > 0;
                    } catch (Exception e) {

                        if (count < list.size()) {
                            queue.addAll(list.subList(count, list.size()));
                        }

                        result = false;
                        log.error("Problem for save history: " + e);
                        e.printStackTrace();
                    }
                    log.info("Save history result = " + result);
                }
            }
        } catch (InterruptedException e) {
            shutdown();
        }
    });

    private DbManager() {
        init = init();
        thread.start();
        qThread.start();
    }

    public static DbManager getInstance() {
        return instance;
    }

    public void addRequest(TransactionRequest request, boolean result) {
        queue.add(new Trw(request, result));
    }

    public void setThreadDisable(boolean threadDisable) {
        this.threadDisable = threadDisable;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public boolean updateName() {
        fileName = "data/transaction_" + fileDateFormat.format(new Date()) + ".log";
        return init();
    }

    private static boolean init() {
        try {
            writer = new FileWriter(fileName);
            return true;
        } catch (IOException e) {
            log.error(e);
            e.printStackTrace();
            return false;
        }
    }

    public synchronized boolean writeString(String s) {
        if (writer != null) {
            try {
                writer.write(s + "\n");
                writer.flush();
                return true;
            } catch (IOException e) {
                log.error("Error write string = " + s);
                e.printStackTrace();
            }
        }
        return false;
    }

    public boolean isInit() {
        return init;
    }

    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void shutdown() {
        close();
        init = false;
    }

    public synchronized boolean setAccounts(AccountService service) {
        this.service = service;
        List<Account> accounts = service.getAllAccounts();
        log.debug("Update accounts: " + accounts);
        map.clear();
        accounts.forEach(account -> map.put(account.getId(), account.getBalance()));
        log.info("Update accounts complete! Size: " + map.size());
        return map.size() > 0;
    }

    public boolean updateAccount(int id, int balance, long trId) {
        String transaction = format("Transact: #%s, -> Set balance id: %s, balance: %s", trId, id, balance);
        if (!writeString(transaction)) return false;
        log.debug(transaction);
        Map<Integer, Integer> map = getCurrentMap();
        log.debug(map);
        if (map.containsKey(id)) {
            getCurrentMap().put(id, balance);
            writeString("Transact: #" + trId + " is OK");
            change.set(true);
            return true;
        }
        return false;
    }

    public boolean updateAccountSync(int id, int balance, long trId) {
        String transaction = format("Transact: #%s, -> Set balance id: %s, balance: %s", trId, id, balance);
        if (!writeString(transaction)) return false;
        log.debug(transaction);
        Map<Integer, Integer> map = getCurrentMapBlock();
        log.debug(map);
        if (map.containsKey(id)) {
            map.put(id, balance);
            writeString("Transact: #" + trId + " is OK");
            state.set(true);
            change.set(true);
            return true;
        }
        state.set(true);
        return false;
    }

    public boolean transferBalance(int mainId, int id, int amount, long trId) {
        String transaction = format("Transact: #%s, -> Main id: %s, id: %s, amount: %s", trId, mainId, id, amount);
        if (!writeString(transaction)) return false;
        Map<Integer, Integer> map = getCurrentMap();
        if (map.containsKey(id) && map.containsKey(mainId) && map.get(id) >= amount) {
            getCurrentMap().computeIfPresent(id, (k, v) -> v - amount);
            getCurrentMap().computeIfPresent(mainId, (k, v) -> v + amount);
            writeString("Transact: #" + trId + " is OK");
            change.set(true);
            return true;
        }
        return false;
    }

    public boolean transferBalanceSync(int mainId, int id, int amount, long trId) {
        String transaction = format("Transact: #%s, -> Main id: %s, id: %s, amount: %s", trId, mainId, id, amount);
        if (!writeString(transaction)) return false;
        Map<Integer, Integer> map = getCurrentMapBlock();
        if (map.containsKey(id) && map.containsKey(mainId) && map.get(id) >= amount) {
            map.computeIfPresent(id, (k, v) -> v - amount);
            map.computeIfPresent(mainId, (k, v) -> v + amount);
            writeString("Transact: #" + trId + " is OK");
            state.set(true);
            change.set(true);
            return true;
        }
        state.set(true);
        return false;
    }

    private synchronized boolean saveMapToDbSync() {
        Map<Integer, Integer> map = getCurrentMapBlock();
        AtomicBoolean result = new AtomicBoolean(true);
        try {
            map.forEach((k, v) -> result.set(result.get() & service.updateAccount(k, v)));
        } catch (Exception e) {
            log.error("Exception while save map! Ex: " + e);
            e.printStackTrace();
            return false;
        } finally {
            change.set(!result.get());
            state.set(true);
        }
        return result.get();
    }

    private synchronized boolean saveMapToDb() {
        state.set(false);
        AtomicBoolean result = new AtomicBoolean(true);
        try {
            map.forEach((k, v) -> result.set(result.get() & service.updateAccount(k, v)));
        } catch (Exception e) {
            log.error("Exception while save map! Ex: " + e);
            e.printStackTrace();
            return false;
        } finally {
            change.set(!result.get());
            state.set(true);
        }
        return result.get();
    }

    private synchronized Map<Integer, Integer> getCurrentMap() {
        state.compareAndSet(true, true);
        return map;
    }

    private synchronized Map<Integer, Integer> getCurrentMapBlock() {
        state.compareAndSet(true, false);
        return map;
    }

    public Map<Integer, Integer> getMap() {
        return getCurrentMap();
    }
}
