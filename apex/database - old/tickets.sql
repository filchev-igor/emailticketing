CREATE TABLE tickets (
                         ticket_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         email_id VARCHAR2(255) UNIQUE NOT NULL,
                         sender_id NUMBER NOT NULL,
                         subject VARCHAR2(255) NOT NULL,
                         body CLOB,
                         status VARCHAR2(50) DEFAULT 'NEW' CHECK (status IN ('NEW', 'IN PROGRESS', 'CLOSED', 'RESOLVED')),
                         creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (sender_id) REFERENCES senders(sender_id)
);
CREATE INDEX idx_tickets_email_id ON tickets(email_id);
CREATE INDEX idx_tickets_creation_date ON tickets(creation_date);

CREATE OR REPLACE TRIGGER trg_tickets_update
BEFORE UPDATE ON tickets
                  FOR EACH ROW
BEGIN
    :NEW.update_date := CURRENT_TIMESTAMP;
END;