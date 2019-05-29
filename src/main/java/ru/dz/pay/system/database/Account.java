package ru.dz.pay.system.database;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Account {
    private int id;
    private int balance;

    public Account() {
    }

    public Account(int id, int balance) {
        this.id = id;
        this.balance = balance;
    }
}
