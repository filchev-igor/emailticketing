CREATE TABLE processed_emails (
                                  email_id VARCHAR2(255) PRIMARY KEY,
                                  creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);