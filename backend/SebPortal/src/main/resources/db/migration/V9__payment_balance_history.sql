-- Historical balances are captured atomically with the debit. Older payments
-- remain null because their original balances cannot be reconstructed reliably.
ALTER TABLE payments ADD COLUMN balance_before_payment NUMERIC(15, 2);
ALTER TABLE payments ADD COLUMN balance_after_payment NUMERIC(15, 2);
