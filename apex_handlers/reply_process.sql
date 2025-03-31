DECLARE
l_admin_email   VARCHAR2(255);
    l_sender_email  VARCHAR2(255);
    l_url           VARCHAR2(1000) := 'https://2744-90-131-40-206.ngrok-free.app/send-email';
    l_json_payload  CLOB;
    l_response      CLOB;
BEGIN
    APEX_DEBUG.INFO('📩 Starting admin reply process');

    l_admin_email := LOWER(:APP_USER);
    APEX_DEBUG.INFO('👤 Admin email: %s', l_admin_email);

SELECT s.sender_email INTO l_sender_email
FROM senders s
         JOIN tickets t ON t.sender_id = s.sender_id
WHERE t.ticket_id = :P4_TICKET_ID;

APEX_DEBUG.INFO('👥 User email: %s', l_sender_email);

INSERT INTO ticket_messages (
    ticket_id, sender_role, sender_id, message_text
) VALUES (
             :P4_TICKET_ID, 'ADMIN', l_admin_email, :P4_WRITE_YOUR_ANSWER
         );

APEX_DEBUG.INFO('✅ Message saved');

    l_json_payload := '{
        "to": "' || l_sender_email || '",
        "from": "' || l_admin_email || '",
        "subject": "' || REPLACE(:P4_TITLE, '"', '\"') || '",
        "body": "' || REPLACE(:P4_WRITE_YOUR_ANSWER, '"', '\"') || '",
        "inReplyTo": "' || :P4_EMAIL_ID || '"
    }';

    APEX_DEBUG.INFO('📤 JSON Payload: %s', l_json_payload);

    apex_web_service.clear_request_headers;
    apex_web_service.set_request_headers(
        p_name_01  => 'x-api-key',
        p_value_01 => '1234-ABCD-5678-EFGH'
    );

    l_response := apex_web_service.make_rest_request(
        p_url           => l_url,
        p_http_method   => 'POST',
        p_body          => l_json_payload,
        p_body_charset  => 'UTF-8',
        p_content_type  => 'application/json'
    );

    APEX_DEBUG.INFO('🔁 Response: %s', l_response);
COMMIT;
APEX_DEBUG.INFO('✅ Reply finished');

EXCEPTION
    WHEN OTHERS THEN
        APEX_DEBUG.ERROR('❌ Error: %s', SQLERRM);
ROLLBACK;
raise;
END;