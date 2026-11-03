ALTER TABLE audit_entries
    ADD COLUMN tenant_id INT REFERENCES tenants(id);