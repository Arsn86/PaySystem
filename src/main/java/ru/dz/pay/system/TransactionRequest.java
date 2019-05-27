package ru.dz.pay.system;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TransactionRequest {
    private long transactionId;
    private int accountId;
    private long dateTime = System.currentTimeMillis();
    private int amount;
    private int type;
}
