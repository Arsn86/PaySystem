package ru.dz.pay.system.helpers.database;

public class AccountNotFoundException extends IllegalArgumentException {

    public AccountNotFoundException() {
    }

    public AccountNotFoundException(String s) {
        super(s);
    }

    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public AccountNotFoundException(Throwable cause) {
        super(cause);
    }
}
