CREATE TABLE senders (
                         sender_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         sender_name VARCHAR2(100) NOT NULL,
                         sender_email VARCHAR2(255) UNIQUE NOT NULL,
                         creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_senders_update
BEFORE UPDATE ON senders
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;