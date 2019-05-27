package ru.dz.pay.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ru.dz.pay.system.helpers.DbManager;
import ru.dz.pay.system.helpers.Strategy;
import ru.dz.pay.system.helpers.database.AccountService;

import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private static final Logger log = LogManager.getLogger(PaymentController.class);

    private static DbManager dbManager = DbManager.getInstance();
    private static final AtomicLong count = new AtomicLong(0);

    @Autowired
    private AccountService service;

    private static boolean mapInitiated = false;

    @GetMapping
    public String show() {
        log.info("All BALANCE: " + service.getAllBalance());
        dbManager.updateName();
        count.set(0);
        initMap();
        return "ALIVE";
    }

    @GetMapping("/show")
    public String onlyShow() {
        return "I'M ALIVE";
    }

    @GetMapping("/stop")
    public String stop() {
        dbManager.setThreadDisable(true);
        return "OK, Manager stopped!";
    }

    @GetMapping("/sync")
    public String sync() {
        dbManager.setSync(true);
        return "OK, Manager sync state!";
    }

    @GetMapping("/async")
    public String async() {
        dbManager.setSync(false);
        return "OK, Manager async state!";
    }

    private synchronized boolean initMap() {
        dbManager.setThreadDisable(false);
        return dbManager.setAccounts(service);
    }

    @PostMapping("/pay")
    public TransactionResponse send(@RequestBody TransactionRequest request) {
        return proceed(request, Strategy.STANDARD);
    }

    @PostMapping("/wdb")
    public TransactionResponse sendWithoutDb(@RequestBody TransactionRequest request) {
        return proceed(request, Strategy.WITHOUTDB);
    }

    @PostMapping("/fast")
    public TransactionResponse sendFast(@RequestBody TransactionRequest request) {
        return proceed(request, Strategy.FAST);
    }

    @PostMapping("/dbt")
    public TransactionResponse sendDbt(@RequestBody TransactionRequest request) {
        return proceed(request, Strategy.DBT);
    }

    private TransactionResponse proceed(TransactionRequest request, Strategy strategy) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionId(request.getTransactionId());

        if (!mapInitiated) mapInitiated = initMap();

        boolean result = false;

        try {
            switch (strategy) {
                case STANDARD:
                    switch (request.getType()) {
                        case 10:
                            result = service.updateAccount(request.getAccountId(), request.getAmount());
                            break;
                        case 12:
                            result = service.transferBalance(-1, request.getAccountId(), request.getAmount());
                            break;
                        case 14:
                            result = service.transferBalance(request.getAccountId(), -1, request.getAmount());
                            break;
                        default:
                            log.error("Incorrect message type! Type = " + request.getType());
                    }
                    break;
                case FAST:
                    switch (request.getType()) {
                        case 10:
                            result = dbManager.updateAccount(request.getAccountId(), request.getAmount(), request.getTransactionId());
                            break;
                        case 12:
                            result = dbManager.transferBalance(-1, request.getAccountId(), request.getAmount(), request.getTransactionId());
                            break;
                        case 14:
                            result = dbManager.transferBalance(request.getAccountId(), -1, request.getAmount(), request.getTransactionId());
                            break;
                        default:
                            log.error("Incorrect message type! Type = " + request.getType());
                    }
                    break;
                case DBT:
                    switch (request.getType()) {
                        case 10:
                            result = dbManager.updateAccountSync(request.getAccountId(), request.getAmount(), request.getTransactionId());
                            break;
                        case 12:
                            result = dbManager.transferBalanceSync(-1, request.getAccountId(), request.getAmount(), request.getTransactionId());
                            break;
                        case 14:
                            result = dbManager.transferBalanceSync(request.getAccountId(), -1, request.getAmount(), request.getTransactionId());
                            break;
                        default:
                            log.error("Incorrect message type! Type = " + request.getType());
                    }
                    break;
            }
        } finally {
            log.info(format("Count: %s | Strategy: %s | Result: %s", count.incrementAndGet(), strategy, result));
            response.setResult(result);
            response.setDateTime(System.currentTimeMillis());
            String s = request.toString() + " -> " + response + " | result = " + result;
            dbManager.addRequest(request, result);
            dbManager.writeString(s);
            log.info(s);
        }

        return response;
    }

}
