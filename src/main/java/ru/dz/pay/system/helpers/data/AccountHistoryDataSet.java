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
@Table(name = "account_history")
public class AccountHistoryDataSet extends DataSet {
    @Column(name = "id")
    private long id;
    @Column(name = "account_id")
    private int accountId;
    @Column(name = "dt")
    private long dt;
    @Column(name = "amount")
    private long amount;
    @Column(name = "type")
    private int type;
    @Column(name = "result")
    private boolean result;
    @Column(name = "transaction_id")
    private long transactionId;
}
