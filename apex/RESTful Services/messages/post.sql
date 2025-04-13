DECLARE
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
    l_expected_key VARCHAR2(200) := '1234-ABCD-5678-EFGH';
    l_json         CLOB;
    l_ticket_id    NUMBER;
    l_user_id      NUMBER;
    l_message_id   NUMBER;
    l_sender_email VARCHAR2(255);
BEGIN
    -- Read the body using TO_CLOB
    l_json := TO_CLOB(:body);
    APEX_DEBUG.INFO('Raw body length: %d', NVL(DBMS_LOB.getlength(l_json), 0));

    -- Set response headers
    owa_util.mime_header('application/json; charset=utf-8', FALSE);
    owa_util.http_header_close;

    -- API Key Authentication
    IF l_provided_key IS NULL OR l_provided_key <> l_expected_key THEN
        owa_util.status_line(401, 'Unauthorized');
        HTP.P('{"error":"Unauthorized"}');
        APEX_DEBUG.ERROR('Unauthorized access attempt');
        RETURN;
END IF;

    -- Check if JSON body is provided
    IF l_json IS NULL OR DBMS_LOB.getlength(l_json) = 0 THEN
        owa_util.status_line(400, 'Bad Request');
        HTP.P('{"error":"Request body is required"}');
        APEX_DEBUG.ERROR('Body is empty');
        RETURN;
END IF;

    -- Parse JSON input
BEGIN
        APEX_JSON.parse(l_json);
        APEX_DEBUG.INFO('JSON parsed successfully');
EXCEPTION
        WHEN OTHERS THEN
            owa_util.status_line(400, 'Bad Request');
            HTP.P('{"error":"JSON parsing failed","details":"' || REPLACE(SQLERRM, '"', '') || '"}');
            APEX_DEBUG.ERROR('JSON parsing failed: %s', SQLERRM);
            RETURN;
END;

    -- Validate required fields
    IF APEX_JSON.get_varchar2('ticket_id') IS NULL THEN
        owa_util.status_line(400, 'Bad Request');
        HTP.P('{"error":"ticket_id is required"}');
        APEX_DEBUG.ERROR('Missing ticket_id');
        RETURN;
END IF;

    IF APEX_JSON.get_varchar2('body') IS NULL THEN
        owa_util.status_line(400, 'Bad Request');
        HTP.P('{"error":"body is required"}');
        APEX_DEBUG.ERROR('Missing body');
        RETURN;
END IF;

    -- Get sender_email
    l_sender_email := APEX_JSON.get_varchar2('sender_email');
    IF l_sender_email IS NULL THEN
        owa_util.status_line(400, 'Bad Request');
        HTP.P('{"error":"sender_email is required"}');
        APEX_DEBUG.ERROR('Missing sender_email');
        RETURN;
END IF;

    -- Validate ticket_id
BEGIN
        l_ticket_id := TO_NUMBER(APEX_JSON.get_varchar2('ticket_id'));
SELECT ticket_id INTO l_ticket_id
FROM tickets
WHERE ticket_id = l_ticket_id;
APEX_DEBUG.INFO('Validated ticket_id: %d', l_ticket_id);
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            owa_util.status_line(400, 'Bad Request');
            HTP.P('{"error":"Invalid ticket_id - ticket does not exist"}');
            APEX_DEBUG.ERROR('Invalid ticket_id: %s', APEX_JSON.get_varchar2('ticket_id'));
            RETURN;
WHEN VALUE_ERROR THEN
            owa_util.status_line(400, 'Bad Request');
            HTP.P('{"error":"ticket_id must be a valid number"}');
            APEX_DEBUG.ERROR('Invalid ticket_id format: %s', APEX_JSON.get_varchar2('ticket_id'));
            RETURN;
END;

    -- Resolve user_id from users table based on sender_email
BEGIN
SELECT user_id INTO l_user_id
FROM users
WHERE email = l_sender_email AND role = 'USER';
APEX_DEBUG.INFO('Found user_id %d for email %s', l_user_id, l_sender_email);
EXCEPTION
        WHEN NO_DATA_FOUND THEN
            -- Fallback: Use the user_id from the ticket
BEGIN
SELECT user_id INTO l_user_id
FROM tickets
WHERE ticket_id = l_ticket_id;
APEX_DEBUG.INFO('Using ticket user_id %d for ticket_id %d', l_user_id, l_ticket_id);
EXCEPTION
                WHEN NO_DATA_FOUND THEN
                    owa_util.status_line(400, 'Bad Request');
                    HTP.P('{"error":"Could not determine user_id for ticket"}');
                    APEX_DEBUG.ERROR('No user_id found for ticket_id %d', l_ticket_id);
                    RETURN;
END;
END;

    -- Insert into messages
INSERT INTO messages (
    ticket_id,
    user_id,
    message_text
) VALUES (
             l_ticket_id,
             l_user_id,
             APEX_JSON.get_varchar2('body')
         ) RETURNING message_id INTO l_message_id;

APEX_DEBUG.INFO('Inserted message_id: %d', l_message_id);

    -- Commit the transaction
COMMIT;

-- Return success response
owa_util.status_line(201, 'Created');
    HTP.P('{"message":"Message created","message_id":' || l_message_id || '}');

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        owa_util.status_line(500, 'Internal Server Error');
        HTP.P('{"error":"Internal Server Error","details":"' || REPLACE(SQLERRM, '"', '') || '"}');
        APEX_DEBUG.ERROR('Unexpected error: %s', SQLERRM);
END;