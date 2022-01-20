ALTER TABLE user ADD email VARCHAR(256) NOT NULL AFTER pwd;
CREATE INDEX mail_index ON user(`email`)
