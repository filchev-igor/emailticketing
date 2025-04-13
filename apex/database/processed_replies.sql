CREATE TABLE processed_replies (
                                   reply_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   email_id VARCHAR2(255) NOT NULL,
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

CREATE INDEX idx_processed_replies_ticket_id ON processed_replies (ticket_id);