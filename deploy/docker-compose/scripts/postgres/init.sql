CREATE USER digitaluser WITH PASSWORD 'Demo123!';
CREATE DATABASE digitalbank;
CREATE DATABASE digitalcredit;
GRANT ALL PRIVILEGES ON DATABASE digitalbank TO digitaluser;
GRANT ALL PRIVILEGES ON DATABASE digitalcredit TO digitaluser;
-- PostgreSQL 15+ no longer lets non-owners create tables in the public schema
ALTER DATABASE digitalbank OWNER TO digitaluser;
ALTER DATABASE digitalcredit OWNER TO digitaluser;
