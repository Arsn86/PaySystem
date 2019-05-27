package ru.dz.pay.system.helpers;

import lombok.Getter;
import lombok.ToString;
import ru.dz.pay.system.TransactionRequest;

@Getter
@ToString
public class Trw {
    TransactionRequest request;
    boolean result;

    public Trw(TransactionRequest request, boolean result) {
        this.request = request;
        this.result = result;
    }
}
