DECLARE
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
   l_expected_key VARCHAR2(200) := '1234-ABCD-5678-EFGH'; -- Replace with your actual key
   l_json         CLOB := :body; -- Incoming JSON payload
   l_ticket_id    NUMBER;
   l_user_id      NUMBER;
   l_message_id   NUMBER;
BEGIN
   -- Set response headers
   owa_util.mime_header('application/json; charset=utf-8', FALSE);
   owa_util.http_header_close;

   -- API Key Authentication
   IF l_provided_key IS NULL OR l_provided_key <> l_expected_key THEN
      owa_util.status_line(401, 'Unauthorized');
      HTP.P('{"error":"Unauthorized"}');
      RETURN;
END IF;

   -- Check if JSON body is provided
   IF l_json IS NULL THEN
      owa_util.status_line(400, 'Bad Request');
      HTP.P('{"error":"Request body is required"}');
      RETURN;
END IF;

   -- Parse JSON input (matching MessageReplyDto)
   APEX_JSON.parse(l_json);

   -- Validate required fields
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

   -- Convert ticket_id to NUMBER and validate it exists
BEGIN
      l_ticket_id := TO_NUMBER(APEX_JSON.get_varchar2('ticket_id'));
SELECT ticket_id
INTO l_ticket_id
FROM tickets
WHERE ticket_id = l_ticket_id;
EXCEPTION
      WHEN NO_DATA_FOUND THEN
         owa_util.status_line(400, 'Bad Request');
         HTP.P('{"error":"Invalid ticket_id - ticket does not exist"}');
         RETURN;
WHEN VALUE_ERROR THEN
         owa_util.status_line(400, 'Bad Request');
         HTP.P('{"error":"ticket_id must be a valid number"}');
         RETURN;
END;

   -- Resolve user_id (example: based on sender_email matching email_id in tickets)
BEGIN
SELECT user_id
INTO l_user_id
FROM tickets
WHERE ticket_id = l_ticket_id
  AND email_id = APEX_JSON.get_varchar2('sender_email');
EXCEPTION
      WHEN NO_DATA_FOUND THEN
         -- Fallback: You might need a default user_id or additional logic
         owa_util.status_line(400, 'Bad Request');
         HTP.P('{"error":"Could not determine user_id from sender_email"}');
         RETURN;
END;

   -- Insert into messages table
INSERT INTO messages (
    ticket_id,
    user_id,
    message_text
) VALUES (
             l_ticket_id,
             l_user_id,
             APEX_JSON.get_varchar2('body')
         ) RETURNING message_id INTO l_message_id;

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
END;