DECLARE
l_email_id tickets.email_id%TYPE;
    l_sender_name senders.sender_name%TYPE;
    l_sender_email senders.sender_email%TYPE;
    l_subject tickets.subject%TYPE;
    l_body tickets.body%TYPE;
    l_sender_id tickets.sender_id%TYPE;
    l_raw_body CLOB;
    l_json_parsed BOOLEAN := FALSE;
BEGIN
    -- Get the raw request body
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

        APEX_DEBUG.INFO('email_id: %s', l_email_id);
        APEX_DEBUG.INFO('sender_name: %s', l_sender_name);
        APEX_DEBUG.INFO('sender_email: %s', l_sender_email);
        APEX_DEBUG.INFO('subject: %s', l_subject);
        APEX_DEBUG.INFO('body: %s', l_body);

        -- Get or insert sender
MERGE INTO senders s
    USING (SELECT l_sender_email AS sender_email FROM dual) d
    ON (s.sender_email = d.sender_email)
    WHEN NOT MATCHED THEN
        INSERT (sender_id, sender_name, sender_email, creation_date, update_date)
            VALUES (DEFAULT, l_sender_name, l_sender_email, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    WHEN MATCHED THEN
        UPDATE SET update_date = CURRENT_TIMESTAMP;

-- Get sender_id for the ticket
SELECT sender_id INTO l_sender_id
FROM senders
WHERE sender_email = l_sender_email;

-- Insert into tickets
INSERT INTO tickets (email_id, sender_id, subject, body)
VALUES (l_email_id, l_sender_id, l_subject, l_body);

COMMIT;

-- Set the Content-Type to application/json
OWA_UTIL.mime_header('application/json', FALSE);

        -- Write success response
        APEX_JSON.initialize_clob_output;
        APEX_JSON.open_object;
        APEX_JSON.write('status', 'success');
        APEX_JSON.write('message', 'Ticket created successfully');
        APEX_JSON.close_object;

ELSE
        -- Set the Content-Type to application/json for error response
        OWA_UTIL.mime_header('application/json', FALSE);

        -- Write error response
        APEX_JSON.initialize_clob_output;
        APEX_JSON.open_object;
        APEX_JSON.write('status', 'error');
        APEX_JSON.write('message', 'JSON parsing failed or body is empty');
        APEX_JSON.close_object;
END IF;

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;

        -- Set the Content-Type to application/json for exception response
        OWA_UTIL.mime_header('application/json', FALSE);

        -- Write exception response
        APEX_JSON.initialize_clob_output;
        APEX_JSON.open_object;
        APEX_JSON.write('status', 'error');
        APEX_JSON.write('message', SQLERRM);
        APEX_JSON.close_object;
END;