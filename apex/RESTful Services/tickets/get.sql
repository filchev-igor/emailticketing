BEGIN
   -- Declare variables for API key
   DECLARE
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
      l_expected_key VARCHAR2(200) := '1234-ABCD-5678-EFGH'; -- Replace with your actual key
      l_found        BOOLEAN := FALSE;
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

      -- Check email_thread_id parameter
      IF :email_thread_id IS NULL THEN
         owa_util.status_line(400, 'Bad Request');
         APEX_JSON.initialize_clob_output;
         APEX_JSON.open_object;
         APEX_JSON.write('error', 'email_thread_id parameter is required');
         APEX_JSON.close_object;
         HTP.P(APEX_JSON.get_clob_output);
         APEX_JSON.free_output;
         RETURN;
END IF;

      -- Initialize JSON output
      APEX_JSON.initialize_clob_output;
      APEX_JSON.open_object;

      -- Query and output ticket data
FOR rec IN (
         SELECT ticket_id,
                email_id,
                email_thread_id,
                subject,
                status
         FROM tickets
         WHERE email_thread_id = :email_thread_id
      ) LOOP
         l_found := TRUE;
         APEX_JSON.write('ticket_id', rec.ticket_id);
         APEX_JSON.write('email_id', rec.email_id);
         APEX_JSON.write('email_thread_id', rec.email_thread_id);
         APEX_JSON.write('subject', rec.subject);
         APEX_JSON.write('status', rec.status);
         EXIT; -- Only one record expected due to UNIQUE constraint
END LOOP;

      -- If no data found, return a message
      IF NOT l_found THEN
         APEX_JSON.write('message', 'No ticket found for the provided email_thread_id');
END IF;

      -- Close JSON object and output
      APEX_JSON.close_object;
      HTP.P(APEX_JSON.get_clob_output);
      APEX_JSON.free_output;

EXCEPTION
      WHEN OTHERS THEN
         APEX_JSON.free_output; -- Clean up JSON state
         owa_util.status_line(500, 'Internal Server Error');
         HTP.P('{"error":"Internal Server Error","details":"' || REPLACE(SQLERRM, '"', '') || '"}');
END;
END;