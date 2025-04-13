CREATE TABLE users (
                       user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       full_name VARCHAR2(100) NOT NULL,
                       email VARCHAR2(255) NOT NULL,
                       role VARCHAR2(20) DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')) NOT NULL,
                       creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       UNIQUE (email),
                       CHECK (REGEXP_LIKE(email, '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'))
);

CREATE OR REPLACE TRIGGER trg_users_update
BEFORE UPDATE ON users
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;

CREATE INDEX idx_users_email ON users (email);