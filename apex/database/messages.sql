CREATE TABLE messages (
                          message_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          ticket_id NUMBER NOT NULL,
                          email_message_id VARCHAR2(255) NOT NULL, -- Removed UNIQUE
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