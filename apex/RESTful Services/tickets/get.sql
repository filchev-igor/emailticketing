BEGIN
   -- Declare variables for API key
   DECLARE
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
      l_expected_key VARCHAR2(200) := '1234-ABCD-5678-EFGH'; -- Replace with your actual key
BEGIN
      -- Set response headers
      owa_util.mime_header('application/json; charset=utf-8', FALSE);
      owa_util.http_header_close;

      -- Check API key
      IF l_provided_key IS NULL OR l_provided_key <> l_expected_key THEN
         owa_util.status_line(401, 'Unauthorized');
         HTP.P('{"error":"Unauthorized"}');
         RETURN;
END IF;

      -- Check email_thread_id parameter
      IF :email_thread_id IS NULL THEN
         HTP.P('{"error":"email_thread_id parameter is required"}');
ELSE
         -- Query the tickets table
         FOR rec IN (
            SELECT ticket_id,
                   email_id,
                   email_thread_id,
                   subject,
                   status
            FROM tickets
            WHERE email_thread_id = :email_thread_id
         ) LOOP
            -- Output JSON for the record
            HTP.P(
               '{"ticket_id":' || rec.ticket_id || ',' ||
               '"email_id":"' || REPLACE(rec.email_id, '"', '\"') || '",' ||
               '"email_thread_id":"' || REPLACE(rec.email_thread_id, '"', '\"') || '",' ||
               '"subject":"' || REPLACE(rec.subject, '"', '\"') || '",' ||
               '"status":"' || REPLACE(rec.status, '"', '\"') || '"}'
            );
            RETURN; -- Return after the first record (assuming one match due to UNIQUE constraint)
END LOOP;
         -- If no records found
         HTP.P('{"message":"No ticket found for the provided email_thread_id"}');
END IF;
END;
EXCEPTION
   WHEN OTHERS THEN
      owa_util.status_line(500, 'Internal Server Error');
      HTP.P('{"error":"Internal Server Error","details":"' || REPLACE(SQLERRM, '"', '') || '"}');
END;