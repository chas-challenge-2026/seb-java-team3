CREATE TABLE payments (
                          id SERIAL PRIMARY KEY,
                          tenant_id INT REFERENCES tenants(id),
                          from_account_id INT REFERENCES accounts(id),
                          to_iban VARCHAR(34),
                          amount DECIMAL(15,2),
                          currency VARCHAR(3) DEFAULT 'SEK',
                          reference VARCHAR(100),
                          status VARCHAR(30) DEFAULT 'pending_approval', -- 'pending_approval', 'completed', 'rejected'
                          created_by INT REFERENCES users(id),
                          created_at TIMESTAMP DEFAULT NOW(),
                          executed_at TIMESTAMP
);

CREATE TABLE approval_steps (
                                id SERIAL PRIMARY KEY,
                                payment_id INT REFERENCES payments(id),
                                attestant_id INT REFERENCES users(id),
                                step_number INT DEFAULT 1,
                                status VARCHAR(20) DEFAULT 'pending', -- 'pending', 'approved', 'rejected'
                                decided_at TIMESTAMP,
                                comment VARCHAR(255)
);