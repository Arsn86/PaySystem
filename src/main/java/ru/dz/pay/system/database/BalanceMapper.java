package ru.dz.pay.system.database;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BalanceMapper implements RowMapper<Long> {
    @Override
    public Long mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getLong("balance");
    }
}
