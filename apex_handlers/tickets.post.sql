DECLARE
-- 1) Check the API key first
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
    l_expected_key VARCHAR2(200) := 'my-x-api-key';

    -- 2) Your existing variables
    l_email_id tickets.email_id%TYPE;
    l_sender_name senders.sender_name%TYPE;
    l_sender_email senders.sender_email%TYPE;
    l_subject tickets.subject%TYPE;
    l_body tickets.body%TYPE;
    l_sender_id tickets.sender_id%TYPE;
    l_gmail_date VARCHAR2(50);
    l_raw_body CLOB;
    l_json_parsed BOOLEAN := FALSE;
BEGIN
    -- 1a) If the key is missing or doesn’t match, return 401 Unauthorized in JSON
    IF l_provided_key IS NULL OR l_provided_key <> l_expected_key THEN
        owa_util.status_line(401, 'Unauthorized');
        owa_util.mime_header('application/json', FALSE);
        htp.print('{"error":"Unauthorized"}');
        RETURN;
END IF;

    -- 2) Continue with normal logic if authorized
    l_raw_body := TO_CLOB(:body);
    APEX_DEBUG.INFO('Raw request body length: %d', NVL(DBMS_LOB.GETLENGTH(l_raw_body), 0));
    APEX_DEBUG.INFO('Raw request body: %s', l_raw_body);

    -- Parse the JSON body
BEGIN
        IF l_raw_body IS NOT NULL AND DBMS_LOB.GETLENGTH(l_raw_body) > 0 THEN
            APEX_JSON.parse(l_raw_body);
            l_json_parsed := TRUE;
ELSE
            APEX_DEBUG.ERROR('Raw request body is empty or null');
END IF;
EXCEPTION
        WHEN OTHERS THEN
            APEX_DEBUG.ERROR('Failed to parse JSON: %s', SQLERRM);
            l_json_parsed := FALSE;
END;

    IF l_json_parsed THEN
        -- Extract JSON fields
        l_email_id := APEX_JSON.get_varchar2(p_path => 'email_id');
        l_sender_name := APEX_JSON.get_varchar2(p_path => 'sender_name');
        l_sender_email := APEX_JSON.get_varchar2(p_path => 'sender_email');
        l_subject := APEX_JSON.get_varchar2(p_path => 'subject');
        l_body := APEX_JSON.get_clob(p_path => 'body');
        l_gmail_date := APEX_JSON.get_varchar2(p_path => 'gmail_date'); -- ✅ Extract gmail_date

        APEX_DEBUG.INFO('email_id: %s', l_email_id);
        APEX_DEBUG.INFO('sender_name: %s', l_sender_name);
        APEX_DEBUG.INFO('sender_email: %s', l_sender_email);
        APEX_DEBUG.INFO('subject: %s', l_subject);
        APEX_DEBUG.INFO('body: %s', l_body);
        APEX_DEBUG.INFO('gmail_date: %s', l_gmail_date);

        -- Upsert sender
MERGE INTO senders s
    USING (SELECT l_sender_email AS sender_email FROM dual) d
    ON (s.sender_email = d.sender_email)
    WHEN NOT MATCHED THEN
        INSERT (sender_id, sender_name, sender_email, creation_date, update_date)
            VALUES (DEFAULT, l_sender_name, l_sender_email, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    WHEN MATCHED THEN
        UPDATE SET update_date = CURRENT_TIMESTAMP;

-- Retrieve sender_id
SELECT sender_id INTO l_sender_id
FROM senders
WHERE sender_email = l_sender_email;

-- Insert into tickets
INSERT INTO tickets (
    email_id, sender_id, subject, body, status, creation_date, update_date
) VALUES (
             l_email_id, l_sender_id, l_subject, l_body, 'NEW',
             TO_TIMESTAMP_TZ(l_gmail_date, 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
             TO_TIMESTAMP_TZ(l_gmail_date, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
         );

-- Insert into processed_emails
BEGIN
INSERT INTO processed_emails (email_id, creation_date, update_date)
VALUES (l_email_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
EXCEPTION
            WHEN DUP_VAL_ON_INDEX THEN
                APEX_DEBUG.INFO('Email ID %s already processed, skipping duplicate insert', l_email_id);
WHEN OTHERS THEN
                APEX_DEBUG.ERROR('Failed to insert into processed_emails: %s', SQLERRM);
END;

COMMIT;

-- Respond with success
OWA_UTIL.mime_header('application/json', FALSE);
        APEX_JSON.initialize_clob_output;
        APEX_JSON.open_object;
        APEX_JSON.write('status', 'success');
        APEX_JSON.write('message', 'Ticket created successfully');
        APEX_JSON.close_object;
ELSE
        -- JSON parsing failed
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
