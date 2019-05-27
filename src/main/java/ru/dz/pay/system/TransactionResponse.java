package ru.dz.pay.system;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TransactionResponse {
    private long transactionId;
    private long dateTime;
    private boolean result;

    public TransactionResponse() {
    }

    public TransactionResponse(long transactionId, long dateTime, boolean result) {
        this.transactionId = transactionId;
        this.dateTime = dateTime;
        this.result = result;
    }

}
