ALTER TABLE users RENAME COLUMN password_md5 TO password_hash;

ALTER TABLE users ALTER COLUMN password_hash TYPE VARCHAR(100);

UPDATE users
SET password_hash = '$2b$12$bY5T.uCVV.y858KslPx/ZOIFx7pfFiFYNKfM1j.kY..i8Qj4vz406'
WHERE password_hash = '482c811da5d5b4bc6d497ffa98491e38';

ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
