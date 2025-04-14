DECLARE
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
    l_expected_key VARCHAR2(200) := '1234-ABCD-5678-EFGH';
    l_json         CLOB;
    l_ticket_id    NUMBER;
    l_user_id      NUMBER;
    l_message_id   NUMBER;
    l_sender_email VARCHAR2(255);
    l_email_id     VARCHAR2(255);
    l_email_message_id VARCHAR2(255);
    l_count        NUMBER;
BEGIN
    l_json := TO_CLOB(:body);
    APEX_DEBUG.INFO('Raw body length: %d', NVL(DBMS_LOB.getlength(l_json), 0));
    owa_util.mime_header('application/json; charset=utf-8', FALSE);
    owa_util.http_header_close;
    IF l_provided_key IS NULL OR l_provided_key <> l_expected_key THEN
        owa_util.status_line(401, 'Unauthorized');
        HTP.P('{"error":"Unauthorized"}');
        RETURN;
END IF;
    IF l_json IS NULL OR DBMS_LOB.getlength(l_json) = 0 THEN
        owa_util.status_line(400, 'Bad Request');
        HTP.P('{"error":"Request body is required"}');
        RETURN;
END IF;
BEGIN
        APEX_JSON.parse(l_json);
EXCEPTION
        WHEN OTHERS THEN
            owa_util.status_line(400, 'Bad Request');
            HTP.P('{"error":"JSON parsing failed","details":"' || REPLACE(SQLERRM, '"', '') || '"}');
            RETURN;
END;
    IF APEX_JSON.get_varchar2('ticket_id') IS NULL THEN
        owa_util.status_line(400, 'Bad Request');
        HTP.P('{"error":"ticket_id is required"}');
        RETURN;
END IF;
    IF APEX_JSON.get_varchar2('body') IS NULL THEN
        owa_util.status_line(400, 'Bad Request');
        HTP.P('{"error":"body is required"}');
        RETURN;
END IF;
    l_sender_email := APEX_JSON.get_varchar2('sender_email');
    l_email_id := APEX_JSON.get_varchar2('email_id');
    l_email_message_id := APEX_JSON.get_varchar2('email_message_id');
    IF l_sender_email IS NULL OR l_email_id IS NULL OR l_email_message_id IS NULL THEN
        owa_util.status_line(400, 'Bad Request');
        HTP.P('{"error":"sender_email, email_id, and email_message_id are required"}');
        RETURN;
END IF;
SELECT COUNT(*) INTO l_count
FROM processed_replies
WHERE email_id = l_email_id;
IF l_count > 0 THEN
        owa_util.status_line(409, 'Conflict');
        HTP.P('{"error":"Reply already processed"}');
        RETURN;
END IF;
BEGIN
        l_ticket_id := TO_NUMBER(APEX_JSON.get_varchar2('ticket_id'));
SELECT ticket_id INTO l_ticket_id
FROM tickets
WHERE ticket_id = l_ticket_id;
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            owa_util.status_line(400, 'Bad Request');
            HTP.P('{"error":"Invalid ticket_id"}');
            RETURN;
WHEN VALUE_ERROR THEN
            owa_util.status_line(400, 'Bad Request');
            HTP.P('{"error":"ticket_id must be a number"}');
            RETURN;
END;
BEGIN
SELECT user_id INTO l_user_id
FROM users
WHERE email = l_sender_email AND role = 'USER';
EXCEPTION
        WHEN NO_DATA_FOUND THEN
BEGIN
SELECT user_id INTO l_user_id
FROM users
WHERE email = l_sender_email AND role = 'ADMIN';
EXCEPTION
                WHEN NO_DATA_FOUND THEN
BEGIN
SELECT user_id INTO l_user_id
FROM tickets
WHERE ticket_id = l_ticket_id;
EXCEPTION
                        WHEN NO_DATA_FOUND THEN
                            owa_util.status_line(400, 'Bad Request');
                            HTP.P('{"error":"Could not determine user_id"}');
                            RETURN;
END;
END;
END;
INSERT INTO messages (
    ticket_id,
    email_message_id,
    user_id,
    message_text
) VALUES (
             l_ticket_id,
             l_email_message_id,
             l_user_id,
             APEX_JSON.get_varchar2('body')
         ) RETURNING message_id INTO l_message_id;
INSERT INTO processed_replies (
    email_id,
    ticket_id,
    creation_date,
    update_date
) VALUES (
             l_email_id,
             l_ticket_id,
             CURRENT_TIMESTAMP,
             CURRENT_TIMESTAMP
         );
COMMIT;
owa_util.status_line(201, 'Created');
    HTP.P('{"message":"Message created","message_id":' || l_message_id || '}');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        owa_util.status_line(500, 'Internal Server Error');
        HTP.P('{"error":"Internal Server Error","details":"' || REPLACE(SQLERRM, '"', '') || '"}');
END;