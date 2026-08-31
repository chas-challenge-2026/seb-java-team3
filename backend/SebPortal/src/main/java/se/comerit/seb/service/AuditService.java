package se.comerit.seb.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;

    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(Integer actor, String action, Integer paymentId) {
        jdbcTemplate.update(
                "INSERT INTO audit_entries (user_id, action, entity_type, entity_id, description) "
                        + "VALUES (?, ?, 'payment', ?, ?)",
                actor,
                action,
                paymentId,
                action + " payment " + paymentId);
    }

    public void record(Integer actor, String action, Integer paymentId, String description) {
        jdbcTemplate.update(
                "INSERT INTO audit_entries (user_id, action, entity_type, entity_id, description) "
                        + "VALUES (?, ?, 'payment', ?, ?)",
                actor,
                action,
                paymentId,
                description);
    }
}
