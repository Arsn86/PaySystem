package ru.dz.pay.system.helpers.data;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@ToString
@Entity
@Table(name = "accounts")
public class AccountsDataSet extends DataSet {
    @Column(name = "id")
    private long id;
    @Column(name = "balance")
    private long balance;

    public AccountsDataSet() {
    }

    public AccountsDataSet(long balance) {
        this.balance = balance;
    }
}
