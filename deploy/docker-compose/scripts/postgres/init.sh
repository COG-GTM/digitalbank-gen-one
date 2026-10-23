#!/bin/bash
set -euo pipefail

: "${DB_APP_PASSWORD:?DB_APP_PASSWORD must be set}"

psql -v ON_ERROR_STOP=1 -v app_password="$DB_APP_PASSWORD" --username "$POSTGRES_USER" <<-'EOSQL'
	CREATE USER digitaluser WITH PASSWORD :'app_password';
	CREATE DATABASE digitalbank;
	CREATE DATABASE digitalcredit;
	GRANT ALL PRIVILEGES ON DATABASE digitalbank TO digitaluser;
	GRANT ALL PRIVILEGES ON DATABASE digitalcredit TO digitaluser;
EOSQL
