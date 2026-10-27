package com.diagramio.infrastructure;

import com.diagramio.domain.DatabaseHealth;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Cài đặt DatabaseHealth bằng câu lệnh "select 1". */
@Component
public class JdbcDatabaseHealth implements DatabaseHealth {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDatabaseHealth(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isUp() {
        try {
            return Integer.valueOf(1).equals(jdbcTemplate.queryForObject("select 1", Integer.class));
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
