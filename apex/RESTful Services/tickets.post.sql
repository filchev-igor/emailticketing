DECLARE
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
    l_expected_key VARCHAR2(200) := '1234-ABCD-5678-EFGH';

    l_email_id tickets.email_id%TYPE;
    l_email_thread_id tickets.email_id%TYPE;
    l_email_message_id tickets.email_id%TYPE;
    l_user_name users.full_name%TYPE;
    l_user_email users.email%TYPE;
    l_subject tickets.subject%TYPE;
    l_body tickets.body%TYPE;
    l_user_id tickets.user_id%TYPE;
    l_gmail_date VARCHAR2(50);
    l_raw_body CLOB;
    l_json_parsed BOOLEAN := FALSE;
BEGIN
    -- Authorization
    IF l_provided_key IS NULL OR l_provided_key <> l_expected_key THEN
        owa_util.status_line(401, 'Unauthorized');
        owa_util.mime_header('application/json', FALSE);
        htp.print('{"error":"Unauthorized"}');
        RETURN;
END IF;

    -- Read and debug body
    l_raw_body := TO_CLOB(:body);
    APEX_DEBUG.INFO('Raw body length: %d', NVL(DBMS_LOB.GETLENGTH(l_raw_body), 0));
    APEX_DEBUG.INFO('Raw body: %s', l_raw_body);

BEGIN
        IF l_raw_body IS NOT NULL AND DBMS_LOB.GETLENGTH(l_raw_body) > 0 THEN
            APEX_JSON.parse(l_raw_body);
            l_json_parsed := TRUE;
ELSE
            APEX_DEBUG.ERROR('Body is empty');
END IF;
EXCEPTION
        WHEN OTHERS THEN
            APEX_DEBUG.ERROR('JSON parsing failed: %s', SQLERRM);
            l_json_parsed := FALSE;
END;

    IF l_json_parsed THEN
        -- Extract fields
        l_email_id := APEX_JSON.get_varchar2('email_id');
        l_email_thread_id := APEX_JSON.get_varchar2('email_thread_id');
        l_email_message_id := APEX_JSON.get_varchar2('email_message_id');
        l_user_name := APEX_JSON.get_varchar2('sender_name');
        l_user_email := APEX_JSON.get_varchar2('sender_email');
        l_subject := APEX_JSON.get_varchar2('subject');
        l_body := APEX_JSON.get_clob('body');
        l_gmail_date := APEX_JSON.get_varchar2('gmail_date');

        APEX_DEBUG.INFO('Parsed email_id: %s', l_email_id);

        -- Upsert user
MERGE INTO users u
    USING (SELECT l_user_email AS email FROM dual) d
    ON (u.email = d.email AND u.role = 'USER')
    WHEN NOT MATCHED THEN
        INSERT (user_id, full_name, email, role, creation_date, update_date)
            VALUES (DEFAULT, l_user_name, l_user_email, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    WHEN MATCHED THEN
        UPDATE SET update_date = CURRENT_TIMESTAMP;

-- Get user_id
SELECT user_id INTO l_user_id
FROM users
WHERE email = l_user_email AND role = 'USER';

-- Insert ticket
INSERT INTO tickets (
    email_id, email_thread_id, email_message_id, user_id, subject, body, status, creation_date, update_date
) VALUES (
             l_email_id, l_email_thread_id, l_email_message_id, l_user_id, l_subject, l_body, 'NEW',
             TO_TIMESTAMP_TZ(l_gmail_date, 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
             TO_TIMESTAMP_TZ(l_gmail_date, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
         );

-- Log processed email
BEGIN
INSERT INTO processed_emails (email_id, creation_date, update_date)
VALUES (l_email_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
EXCEPTION
            WHEN DUP_VAL_ON_INDEX THEN
                APEX_DEBUG.INFO('Email already processed: %s', l_email_id);
WHEN OTHERS THEN
                APEX_DEBUG.ERROR('Insert into processed_emails failed: %s', SQLERRM);
END;

COMMIT;

-- Respond success
OWA_UTIL.mime_header('application/json', FALSE);
        APEX_JSON.initialize_clob_output;
        APEX_JSON.open_object;
        APEX_JSON.write('status', 'success');
        APEX_JSON.write('message', 'Ticket created successfully');
        APEX_JSON.close_object;

ELSE
        -- Parsing failed
        OWA_UTIL.mime_header('application/json', FALSE);
        APEX_JSON.initialize_clob_output;
        APEX_JSON.open_object;
        APEX_JSON.write('status', 'error');
        APEX_JSON.write('message', 'JSON parsing failed or body is empty');
        APEX_JSON.close_object;
END IF;

EXCEPTION
    WHEN DUP_VAL_ON_INDEX THEN
        ROLLBACK;
        OWA_UTIL.mime_header('application/json', FALSE);
        APEX_JSON.initialize_clob_output;
        APEX_JSON.open_object;
        APEX_JSON.write('status', 'error');
        APEX_JSON.write('message', 'Ticket already exists for email_id: ' || l_email_id);
        APEX_JSON.close_object;
WHEN OTHERS THEN
        ROLLBACK;
        OWA_UTIL.mime_header('application/json', FALSE);
        APEX_JSON.initialize_clob_output;
        APEX_JSON.open_object;
        APEX_JSON.write('status', 'error');
        APEX_JSON.write('message', SQLERRM);
        APEX_JSON.close_object;
END;
