CREATE TABLE accounts (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(40) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE auth_sessions (
  token_hash VARCHAR(64) PRIMARY KEY,
  account_id VARCHAR(36) NOT NULL REFERENCES accounts(id),
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_sessions_account ON auth_sessions(account_id);
