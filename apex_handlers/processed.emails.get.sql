DECLARE
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
   l_expected_key VARCHAR2(200) := '1234-ABCD-5678-EFGH';
BEGIN
   owa_util.mime_header('application/json; charset=utf-8', FALSE);
   owa_util.http_header_close;

   IF l_provided_key IS NULL OR l_provided_key <> l_expected_key THEN
     owa_util.status_line(401, 'Unauthorized');
     htp.print('{"error":"Unauthorized"}');
     RETURN;
END IF;

   APEX_JSON.initialize_clob_output;
   -- Open a top-level OBJECT so we can have "items": [...]
   APEX_JSON.open_object;

   -- Then open the array property named "items"
   APEX_JSON.open_array('items');

FOR rec IN (SELECT email_id FROM processed_emails) LOOP
       APEX_JSON.open_object;
       APEX_JSON.write('email_id', rec.email_id);
       APEX_JSON.close_object;
END LOOP;

   APEX_JSON.close_array;
   -- Close the top-level OBJECT
   APEX_JSON.close_object;

   htp.print(APEX_JSON.get_clob_output);
   APEX_JSON.free_output;
END;
