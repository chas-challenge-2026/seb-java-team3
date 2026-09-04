CREATE TABLE tenants (
                         id SERIAL PRIMARY KEY,
                         name VARCHAR(100)
);

CREATE TABLE users (
                       id SERIAL PRIMARY KEY,
                       tenant_id INT REFERENCES tenants(id),
                       name VARCHAR(100),
                       email VARCHAR(100) UNIQUE,
                       password_md5 VARCHAR(32),
                       role VARCHAR(20) -- 'initiator', 'attestant', 'admin'
);