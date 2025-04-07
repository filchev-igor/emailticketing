DECLARE
l_admin_email   VARCHAR2(255);
    l_sender_email  VARCHAR2(255);
    l_ticket_id     NUMBER := :P4_TICKET_ID;
    l_email_id      VARCHAR2(255) := :P4_EMAIL_ID;
    l_admin_id      NUMBER;
    l_url           VARCHAR2(1000) := 'https://16af-90-131-35-89.ngrok-free.app/send-email';
    l_json_payload  CLOB;
    l_response      CLOB;
BEGIN
    -- Get current APEX user email
    l_admin_email := LOWER(:APP_USER);

    -- Ensure the admin exists in users table
MERGE INTO users u
    USING (SELECT l_admin_email AS email FROM dual) d
    ON (u.email = d.email AND u.role = 'ADMIN')
    WHEN NOT MATCHED THEN
        INSERT (user_id, full_name, email, role, creation_date, update_date)
            VALUES (DEFAULT, l_admin_email, l_admin_email, 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    WHEN MATCHED THEN
        UPDATE SET update_date = CURRENT_TIMESTAMP;

-- Get admin's user_id
BEGIN
SELECT user_id INTO l_admin_id
FROM users
WHERE email = l_admin_email AND role = 'ADMIN';
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            APEX_DEBUG.ERROR('❌ Admin user not found even after MERGE.');
            RAISE_APPLICATION_ERROR(-20001, 'Admin user not found.');
END;

    -- Get sender's email based on ticket
BEGIN
SELECT u.email INTO l_sender_email
FROM tickets t
         JOIN users u ON t.user_id = u.user_id
WHERE t.ticket_id = l_ticket_id;
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            APEX_DEBUG.ERROR('❌ No ticket/user found for ticket_id: ' || l_ticket_id);
            RAISE_APPLICATION_ERROR(-20002, 'Ticket not found for provided ticket ID.');
END;

    -- Insert admin's reply into messages
INSERT INTO messages (
    ticket_id, user_id, message_text, creation_date, update_date
) VALUES (
             l_ticket_id, l_admin_id, :P4_WRITE_YOUR_ANSWER, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
         );

-- Build JSON for backend
l_json_payload :=
        '{' ||
        '"to": "' || l_sender_email || '",' ||
        '"from": "' || l_admin_email || '",' ||
        '"subject": "' || REPLACE(:P4_TITLE, '"', '\"') || '",' ||
        '"body": "' || REPLACE(:P4_WRITE_YOUR_ANSWER, '"', '\"') || '",' ||
        '"emailId": "' || l_email_id || '"' ||
        '}';

    -- Send JSON to backend
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
        APEX_DEBUG.ERROR('❌ Unexpected Error: %s', SQLERRM);
ROLLBACK;
RAISE;
END;
