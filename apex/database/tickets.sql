CREATE TABLE tickets (
                         ticket_id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         email_id        VARCHAR2(255) NOT NULL UNIQUE,
                         email_thread_id       VARCHAR2(255) NOT NULL UNIQUE,
                         email_message_id      VARCHAR2(255) NOT NULL UNIQUE,
                         subject         VARCHAR2(255) NOT NULL,
                         body            CLOB,
                         user_id         NUMBER NOT NULL,
                         status          VARCHAR2(50) DEFAULT 'NEW' CHECK (status IN ('NEW', 'IN PROGRESS', 'CLOSED', 'RESOLVED')),
                         creation_date   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         update_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_user FOREIGN KEY (user_id)
        REFERENCES users (user_id);

CREATE OR REPLACE TRIGGER trg_tickets_update
BEFORE UPDATE ON tickets
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;