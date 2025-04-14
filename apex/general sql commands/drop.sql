BEGIN
FOR tbl IN (SELECT table_name
                FROM user_tables)
    LOOP
        EXECUTE IMMEDIATE 'DROP TABLE "' || tbl.table_name || '" CASCADE CONSTRAINTS';
END LOOP;
END;
/