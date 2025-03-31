DECLARE
l_admin_email   VARCHAR2(255);
    l_sender_email  VARCHAR2(255);
    l_ticket_id     NUMBER;
    l_url           VARCHAR2(1000) := 'https://2744-90-131-40-206.ngrok-free.app/send-email';
    l_json_payload  CLOB;
    l_response      CLOB;
BEGIN
    l_admin_email := LOWER(:APP_USER);

SELECT t.ticket_id, s.sender_email
INTO l_ticket_id, l_sender_email
FROM tickets t
         JOIN senders s ON t.sender_id = s.sender_id
WHERE t.email_id = :P4_EMAIL_ID;

INSERT INTO ticket_messages (
    ticket_id, sender_role, sender_id, message_text
) VALUES (
             l_ticket_id, 'ADMIN', l_admin_email, :P4_WRITE_YOUR_ANSWER
         );

-- Подготовка JSON тела запроса
l_json_payload :=
        '{' ||
        '"to": "' || l_sender_email || '",' ||
        '"from": "' || l_admin_email || '",' ||
        '"subject": "' || REPLACE(:P4_TITLE, '"', '\"') || '",' ||
        '"body": "' || REPLACE(:P4_WRITE_YOUR_ANSWER, '"', '\"') || '",' ||
        '"inReplyTo": "' || :P4_EMAIL_ID || '"' ||
        '}';

    -- Очистка и установка заголовков
    apex_web_service.clear_request_headers;
    apex_web_service.set_request_headers(
        p_name_01  => 'Content-Type',
        p_value_01 => 'application/json',
        p_name_02  => 'x-api-key',
        p_value_02 => '1234-ABCD-5678-EFGH'
    );

    -- Отправка POST запроса
    l_response := apex_web_service.make_rest_request(
        p_url         => l_url,
        p_http_method => 'POST',
        p_body        => l_json_payload
    );

    APEX_DEBUG.INFO('✅ Response: %s', l_response);
COMMIT;

EXCEPTION
    WHEN OTHERS THEN
        APEX_DEBUG.ERROR('❌ Error: %s', SQLERRM);
ROLLBACK;
raise;
END;