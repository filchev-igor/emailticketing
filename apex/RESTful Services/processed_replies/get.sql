DECLARE
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
    l_expected_key VARCHAR2(200) := '1234-ABCD-5678-EFGH';
    l_first BOOLEAN := TRUE;
BEGIN
    owa_util.mime_header('application/json; charset=utf-8', FALSE);
    owa_util.http_header_close;
    IF l_provided_key IS NULL OR l_provided_key <> l_expected_key THEN
        owa_util.status_line(401, 'Unauthorized');
        HTP.P('{"error":"Unauthorized"}');
        RETURN;
END IF;
    HTP.P('{"items":[');
FOR rec IN (
        SELECT email_id
        FROM processed_replies
        ORDER BY creation_date
    ) LOOP
        IF NOT l_first THEN
            HTP.P(',');
END IF;
        HTP.P('"' || APEX_ESCAPE.json(rec.email_id) || '"');
        l_first := FALSE;
END LOOP;
    HTP.P(']}');
    owa_util.status_line(200, 'OK');
EXCEPTION
    WHEN OTHERS THEN
        owa_util.status_line(500, 'Internal Server Error');
        HTP.P('{"error":"Internal Server Error","details":"' || REPLACE(SQLERRM, '"', '') || '"}');
END;
/