#!/bin/sh
set -eu
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
 --set=identity_password="$IDENTITY_DB_PASSWORD" --set=catalog_password="$CATALOG_DB_PASSWORD" \
 --set=trade_password="$TRADE_DB_PASSWORD" --set=member_password="$MEMBER_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE lectus_identity LOGIN PASSWORD %L', :'identity_password') \gexec
SELECT format('CREATE ROLE lectus_catalog LOGIN PASSWORD %L', :'catalog_password') \gexec
SELECT format('CREATE ROLE lectus_trade LOGIN PASSWORD %L', :'trade_password') \gexec
SELECT format('CREATE ROLE lectus_member LOGIN PASSWORD %L', :'member_password') \gexec
CREATE DATABASE lectus_identity OWNER lectus_identity;
CREATE DATABASE lectus_catalog OWNER lectus_catalog;
CREATE DATABASE lectus_trade OWNER lectus_trade;
CREATE DATABASE lectus_member OWNER lectus_member;
REVOKE CONNECT ON DATABASE lectus_identity FROM PUBLIC;
REVOKE CONNECT ON DATABASE lectus_catalog FROM PUBLIC;
REVOKE CONNECT ON DATABASE lectus_trade FROM PUBLIC;
REVOKE CONNECT ON DATABASE lectus_member FROM PUBLIC;
SQL
