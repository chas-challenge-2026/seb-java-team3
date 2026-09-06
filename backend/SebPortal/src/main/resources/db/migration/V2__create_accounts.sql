CREATE TABLE accounts (
                          id SERIAL PRIMARY KEY,
                          tenant_id INT REFERENCES tenants(id),
                          account_name VARCHAR(100),
                          iban VARCHAR(34),
                          balance DECIMAL(15,2) DEFAULT 0,
                          currency VARCHAR(3) DEFAULT 'SEK'
);