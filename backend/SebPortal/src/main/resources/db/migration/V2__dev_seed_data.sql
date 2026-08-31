INSERT INTO tenants (id, name) VALUES
(1, 'Malmö Bygg AB')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, tenant_id, name, email, password_md5, role) VALUES
(1, 1, 'Lisa Persson', 'lisa@malmobygg.se', '482c811da5d5b4bc6d497ffa98491e38', 'initiator'),
(2, 1, 'Johan Berg', 'johan@malmobygg.se', '482c811da5d5b4bc6d497ffa98491e38', 'attestant'),
(3, 1, 'Sara Ek', 'sara@malmobygg.se', '482c811da5d5b4bc6d497ffa98491e38', 'admin')
ON CONFLICT (id) DO NOTHING;

INSERT INTO accounts (id, tenant_id, account_name, iban, balance, currency) VALUES
(1, 1, 'Driftkonto', 'SE4550000000058398257466', 2500000.00, 'SEK'),
(2, 1, 'Lönekonto', 'SE4550000000058398257467', 890000.00, 'SEK'),
(3, 1, 'Projektkonto', 'SE4550000000058398257468', 450000.00, 'SEK')
ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, tenant_id, from_account_id, to_iban, amount, reference, status, created_by, executed_at) VALUES
(1, 1, 1, 'SE8550000000054910000003', 15000.00, 'Faktura #1042', 'completed', 1, NOW()),
(2, 1, 1, 'SE8550000000054910000004', 75000.00, 'Faktura #1043', 'pending_approval', 1, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_steps (id, payment_id, attestant_id, step_number, status) VALUES
(1, 2, 2, 1, 'pending')
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_entries (id, user_id, action, entity_type, entity_id, description) VALUES
(1, 1, 'CREATE_PAYMENT', 'payment', 1, 'Skapade betalning 15000 SEK till SE8550000000054910000003'),
(2, 1, 'CREATE_PAYMENT', 'payment', 2, 'Skapade betalning 75000 SEK till SE8550000000054910000004')
ON CONFLICT (id) DO NOTHING;

SELECT setval('tenants_id_seq', (SELECT MAX(id) FROM tenants));
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('accounts_id_seq', (SELECT MAX(id) FROM accounts));
SELECT setval('payments_id_seq', (SELECT MAX(id) FROM payments));
SELECT setval('approval_steps_id_seq', (SELECT MAX(id) FROM approval_steps));
SELECT setval('audit_entries_id_seq', (SELECT MAX(id) FROM audit_entries));
