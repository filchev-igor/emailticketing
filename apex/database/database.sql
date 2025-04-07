CREATE TABLE tickets (
                         ticket_id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         email_id        VARCHAR2(255) NOT NULL UNIQUE,
                         email_thread_id       VARCHAR2(255) NOT NULL UNIQUE,
                         email_message_id      VARCHAR2(255) NOT NULL UNIQUE,
                         subject         VARCHAR2(255) NOT NULL,
                         body            CLOB NOT NULL,
                         user_id         NUMBER NOT NULL,
                         status          VARCHAR2(50) DEFAULT 'NEW' CHECK (status IN ('NEW', 'IN PROGRESS', 'CLOSED', 'RESOLVED')),
                         creation_date   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         update_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE processed_emails (
                                  email_id VARCHAR2(255) PRIMARY KEY,
                                  creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
                       user_id         NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       full_name       VARCHAR2(100) NOT NULL,
                       email           VARCHAR2(255) NOT NULL,
                       role           VARCHAR2(20) DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')) NOT NULL,
                       creation_date   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       update_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       UNIQUE (email, role)
);

CREATE TABLE messages (
                          message_id      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          ticket_id       NUMBER NOT NULL,
                          user_id         NUMBER NOT NULL, -- Can be either user or admin
                          message_text    CLOB NOT NULL,
                          creation_date   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          update_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_messages_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (ticket_id),
                          CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE OR REPLACE TRIGGER trg_messages_update
BEFORE UPDATE ON messages
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_user FOREIGN KEY (user_id)
        REFERENCES users (user_id);

CREATE OR REPLACE TRIGGER trg_tickets_update
BEFORE UPDATE ON tickets
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;

CREATE OR REPLACE TRIGGER trg_users_update
BEFORE UPDATE ON users
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;