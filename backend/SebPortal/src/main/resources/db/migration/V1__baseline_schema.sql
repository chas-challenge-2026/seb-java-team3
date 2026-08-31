CREATE TABLE tenants (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_md5 VARCHAR(32) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant_role ON users(tenant_id, role);

CREATE TABLE accounts (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL REFERENCES tenants(id),
    account_name VARCHAR(255) NOT NULL,
    iban VARCHAR(34) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'SEK',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_tenant ON accounts(tenant_id);

CREATE TABLE payments (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL REFERENCES tenants(id),
    from_account_id INTEGER NOT NULL REFERENCES accounts(id),
    to_iban VARCHAR(34) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'SEK',
    reference VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    executed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_payments_tenant_created_at ON payments(tenant_id, created_at DESC);
CREATE INDEX idx_payments_status ON payments(status);

CREATE TABLE approval_steps (
    id SERIAL PRIMARY KEY,
    payment_id INTEGER NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    attestant_id INTEGER NOT NULL REFERENCES users(id),
    step_number INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    decided_at TIMESTAMP,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_steps_payment ON approval_steps(payment_id);
CREATE INDEX idx_approval_steps_attestant_status ON approval_steps(attestant_id, status);

CREATE TABLE audit_entries (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id INTEGER,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entries_created_at ON audit_entries(created_at DESC);
CREATE INDEX idx_audit_entries_entity ON audit_entries(entity_type, entity_id);

CREATE TABLE notification_failures (
    id SERIAL PRIMARY KEY,
    recipient_user_id INTEGER REFERENCES users(id),
    payment_id INTEGER REFERENCES payments(id) ON DELETE SET NULL,
    channel VARCHAR(50) NOT NULL,
    error_message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_failures_created_at ON notification_failures(created_at DESC);

CREATE TABLE idempotency_keys (
    id SERIAL PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    payment_id INTEGER REFERENCES payments(id) ON DELETE SET NULL,
    created_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
