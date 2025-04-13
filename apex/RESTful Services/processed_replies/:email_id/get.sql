DECLARE
l_provided_key VARCHAR2(200) := owa_util.get_cgi_env('x-api-key');
    l_expected_key VARCHAR2(200) := '1234-ABCD-5678-EFGH';
    l_email_id VARCHAR2(255) := :email_id;
    l_count NUMBER;
BEGIN
    owa_util.mime_header('application/json; charset=utf-8', FALSE);
    owa_util.http_header_close;
    IF l_provided_key IS NULL OR l_provided_key <> l_expected_key THEN
        owa_util.status_line(401, 'Unauthorized');
        HTP.P('{"error":"Unauthorized"}');
        RETURN;
END IF;
    IF l_email_id IS NULL THEN
        owa_util.status_line(400, 'Bad Request');
        HTP.P('{"error":"email_id is required"}');
        RETURN;
END IF;
SELECT COUNT(*) INTO l_count
FROM processed_replies
WHERE email_id = l_email_id;
IF l_count > 0 THEN
        owa_util.status_line(200, 'OK');
        HTP.P('{"status":"processed"}');
ELSE
        owa_util.status_line(404, 'Not Found');
        HTP.P('{"status":"not_processed"}');
END IF;
EXCEPTION
    WHEN OTHERS THEN
        owa_util.status_line(500, 'Internal Server Error');
        HTP.P('{"error":"Internal Server Error","details":"' || REPLACE(SQLERRM, '"', '') || '"}');
END;
/