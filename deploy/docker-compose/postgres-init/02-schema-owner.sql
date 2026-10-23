-- PostgreSQL 15+ no longer grants CREATE on schema public to every user;
-- hand the public schema of each database to the application user.
\connect digitalbank
ALTER SCHEMA public OWNER TO digitaluser;
\connect digitalcredit
ALTER SCHEMA public OWNER TO digitaluser;
