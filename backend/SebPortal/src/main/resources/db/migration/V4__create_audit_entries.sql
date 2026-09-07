CREATE TABLE audit_entries (
                               id SERIAL PRIMARY KEY,
                               user_id INT,
                               action VARCHAR(100),
                               entity_type VARCHAR(50),
                               entity_id INT,
                               description TEXT,
                               created_at TIMESTAMP DEFAULT NOW()
);