-- Drop tables in reverse order to handle foreign keys
BEGIN
EXECUTE IMMEDIATE 'DROP TABLE messages CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
EXECUTE IMMEDIATE 'DROP TABLE processed_replies CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
EXECUTE IMMEDIATE 'DROP TABLE processed_emails CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
EXECUTE IMMEDIATE 'DROP TABLE tickets CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
EXECUTE IMMEDIATE 'DROP TABLE users CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

-- Create users table
CREATE TABLE users (
                       user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       full_name VARCHAR2(100) NOT NULL,
                       email VARCHAR2(255) NOT NULL,
                       role VARCHAR2(20) DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')) NOT NULL,
                       creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       UNIQUE (email, role),
                       CHECK (email LIKE '%_@_%._%')
);

CREATE OR REPLACE TRIGGER trg_users_update
BEFORE UPDATE ON users
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;
/

CREATE INDEX idx_users_email ON users (email);

-- Create tickets table
CREATE TABLE tickets (
                         ticket_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         email_id VARCHAR2(255) NOT NULL UNIQUE,
                         email_thread_id VARCHAR2(255) NOT NULL UNIQUE,
                         email_message_id VARCHAR2(255) NOT NULL UNIQUE,
                         subject VARCHAR2(1000) NOT NULL,
                         body CLOB,
                         user_id NUMBER NOT NULL,
                         status VARCHAR2(50) DEFAULT 'NEW' CHECK (status IN ('NEW', 'IN PROGRESS', 'CLOSED', 'RESOLVED')),
                         creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         CONSTRAINT fk_tickets_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE OR REPLACE TRIGGER trg_tickets_update
BEFORE UPDATE ON tickets
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;
/

-- Create processed_emails table
CREATE TABLE processed_emails (
                                  email_id VARCHAR2(255) PRIMARY KEY,
                                  creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE TRIGGER trg_processed_emails_update
BEFORE UPDATE ON processed_emails
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;
/

-- Create processed_replies table
CREATE TABLE processed_replies (
                                   email_id VARCHAR2(255) PRIMARY KEY,
                                   ticket_id NUMBER NOT NULL,
                                   creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   CONSTRAINT fk_processed_replies_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (ticket_id)
);

CREATE OR REPLACE TRIGGER trg_processed_replies_update
BEFORE UPDATE ON processed_replies
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;
/

CREATE INDEX idx_processed_replies_ticket_id ON processed_replies (ticket_id);

-- Create messages table
CREATE TABLE messages (
                          message_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          ticket_id NUMBER NOT NULL,
                          email_message_id VARCHAR2(255) NOT NULL UNIQUE,
                          user_id NUMBER NOT NULL,
                          message_text CLOB NOT NULL,
                          creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_messages_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (ticket_id),
                          CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE OR REPLACE TRIGGER trg_messages_update
BEFORE UPDATE ON messages
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;
/

CREATE INDEX idx_messages_creation_date ON messages (ticket_id, creation_date);