DECLARE
l_admin_email      VARCHAR2(255);
    l_sender_email     VARCHAR2(255);
    l_ticket_id        NUMBER := :P4_TICKET_ID;
    l_email_id         VARCHAR2(255) := :P4_EMAIL_ID;
    l_email_thread_id  VARCHAR2(255);
    l_email_message_id VARCHAR2(255);
    l_admin_id         NUMBER;
    l_url              VARCHAR2(1000) := 'https://2a7a-90-131-47-209.ngrok-free.app/send-email';
    l_json_payload     CLOB;
    l_response         CLOB;
    l_current_status   VARCHAR2(50);
    l_message_count    NUMBER;
    l_new_message_id   VARCHAR2(255);
    l_table_count      NUMBER;
    l_schema_name      VARCHAR2(128);
BEGIN
    -- Log initial context
    APEX_DEBUG.INFO('📋 Starting reply process for ticket_id: %s, email_id: %s, session: %s',
                    l_ticket_id, l_email_id, :APP_SESSION);

    -- Get schema name
SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') INTO l_schema_name FROM dual;
APEX_DEBUG.INFO('🏛️ Current schema: %s', l_schema_name);

    -- Get current APEX user email
    l_admin_email := LOWER(:APP_USER);
    APEX_DEBUG.INFO('📧 Admin email: %s', l_admin_email);

    -- Ensure the admin exists
MERGE INTO users u
    USING (SELECT l_admin_email AS email FROM dual) d
    ON (u.email = d.email AND u.role = 'ADMIN')
    WHEN NOT MATCHED THEN
        INSERT (user_id, full_name, email, role, creation_date, update_date)
            VALUES (DEFAULT, l_admin_email, l_admin_email, 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    WHEN MATCHED THEN
        UPDATE SET update_date = CURRENT_TIMESTAMP;

-- Get admin user ID
BEGIN
SELECT user_id INTO l_admin_id
FROM users
WHERE email = l_admin_email AND role = 'ADMIN';
APEX_DEBUG.INFO('👤 Admin user_id: %s', l_admin_id);
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            APEX_DEBUG.ERROR('❌ Admin user not found for email: %s', l_admin_email);
            RAISE_APPLICATION_ERROR(-20001, 'Admin user not found.');
END;

    -- Get ticket-related data and status
BEGIN
SELECT u.email, t.email_thread_id, t.email_message_id, t.status
INTO l_sender_email, l_email_thread_id, l_email_message_id, l_current_status
FROM tickets t
         JOIN users u ON t.user_id = u.user_id
WHERE t.ticket_id = l_ticket_id;
APEX_DEBUG.INFO('🎫 Ticket found: sender_email=%s, thread_id=%s, status=%s',
                        l_sender_email, l_email_thread_id, l_current_status);
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            APEX_DEBUG.ERROR('❌ No ticket/user found for ticket_id: %s', l_ticket_id);
            RAISE_APPLICATION_ERROR(-20002, 'Ticket not found for provided ticket ID.');
END;

    -- Check if this is the first reply for the ticket
SELECT COUNT(*) INTO l_message_count
FROM messages
WHERE ticket_id = l_ticket_id;
APEX_DEBUG.INFO('📬 Message count for ticket_id %s: %s', l_ticket_id, l_message_count);

    -- Update ticket status to 'IN PROGRESS' if it's 'NEW' and this is the first reply
    IF l_current_status = 'NEW' AND l_message_count = 0 THEN
UPDATE tickets
SET status = 'IN PROGRESS',
    update_date = CURRENT_TIMESTAMP
WHERE ticket_id = l_ticket_id;
APEX_DEBUG.INFO('🔄 Updated ticket status to IN PROGRESS for ticket_id: %s', l_ticket_id);
END IF;

    -- Verify messages table state
SELECT COUNT(*) INTO l_table_count
FROM messages;
APEX_DEBUG.INFO('📊 Total rows in messages table: %s', l_table_count);

    -- Generate email_message_id
    l_new_message_id := SYS_GUID();
    APEX_DEBUG.INFO('🆔 Generated email_message_id: %s', l_new_message_id);

    -- Insert message
BEGIN
INSERT INTO messages (
    ticket_id, email_message_id, user_id, message_text, creation_date, update_date
) VALUES (
             l_ticket_id, l_new_message_id, l_admin_id, :P4_WRITE_YOUR_ANSWER,
             CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
         );
APEX_DEBUG.INFO('✅ Message inserted for ticket_id: %s, email_message_id: %s',
                        l_ticket_id, l_new_message_id);
EXCEPTION
        WHEN OTHERS THEN
            APEX_DEBUG.ERROR('❌ Insert error: SQLCODE=%s, SQLERRM=%s', SQLCODE, SQLERRM);
ROLLBACK;
RAISE;
END;

    -- Build JSON payload
    l_json_payload :=
        '{' ||
        '"to": "' || l_sender_email || '",' ||
        '"from": "' || l_admin_email || '",' ||
        '"subject": "' || REPLACE(:P4_TITLE, '"', '\"') || '",' ||
        '"body": "' || REPLACE(:P4_WRITE_YOUR_ANSWER, '"', '\"') || '",' ||
        '"emailId": "' || l_email_id || '",' ||
        '"emailThreadId": "' || l_email_thread_id || '",' ||
        '"emailMessageId": "' || l_new_message_id || '"' ||
        '}';
    APEX_DEBUG.INFO('📤 JSON payload: %s', l_json_payload);

    -- Send the request
    apex_web_service.clear_request_headers;
    apex_web_service.set_request_headers(
        p_name_01  => 'Content-Type',
        p_value_01 => 'application/json',
        p_name_02  => 'x-api-key',
        p_value_02 => '1234-ABCD-5678-EFGH'
    );

    l_response := apex_web_service.make_rest_request(
        p_url         => l_url,
        p_http_method => 'POST',
        p_body        => l_json_payload
    );
    APEX_DEBUG.INFO('✅ Response: %s', l_response);

COMMIT;

EXCEPTION
    WHEN OTHERS THEN
        APEX_DEBUG.ERROR('❌ Unexpected error: SQLCODE=%s, SQLERRM=%s, ticket_id=%s, email_message_id=%s',
                         SQLCODE, SQLERRM, l_ticket_id, l_new_message_id);
ROLLBACK;
RAISE;
END;