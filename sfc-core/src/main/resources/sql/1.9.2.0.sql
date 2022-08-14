UPDATE config SET `key` = 'sys.register.enable_reg_code' WHERE `key` = 'ENABLE_REG_CODE';
UPDATE config SET `key` = 'sys.register.enable_email_reg' WHERE `key` = 'ENABLE_EMAIL_REG';
UPDATE config SET `key` = 'sys.register.reg_code' WHERE `key` = 'REG_CODE';

UPDATE config SET `key` = 'sys.store.mode' WHERE `key` = 'STORE_MODE';
UPDATE config SET `key` = 'sys.store.sync_interval' WHERE `key` = 'SYNC_INTERVAL';

UPDATE config SET `key` = 'sys.common.version' WHERE `key` = 'VERSION';
UPDATE config SET `key` = 'sys.common.token_secret' WHERE `key` = 'TOKEN_SECRET';
UPDATE config SET `key` = 'sys.common.mail_properties' WHERE `key` = 'MAIL_PROPERTIES';
UPDATE config SET `key` = 'sys.common.ftp_properties' WHERE `key` = 'FTP_PROPERTIES';