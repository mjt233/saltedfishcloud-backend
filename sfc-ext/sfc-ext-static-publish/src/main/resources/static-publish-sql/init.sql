CREATE TABLE static_publish_record
(
    id                  BIGINT       NOT NULL,
    uid                 BIGINT       NULL,
    create_at           datetime     NULL,
    update_at           datetime     NULL,
    site_name           VARCHAR(255) NULL,
    username            VARCHAR(255) NULL,
    access_way          INT          NULL,
    `path`              VARCHAR(255) NULL,
    is_enable_index     BIT(1)       NULL,
    is_enable_file_list BIT(1)       NULL,
    is_need_login       BIT(1)       NULL,
    login_username      VARCHAR(32)  NULL,
    login_password      VARCHAR(255) NULL,
    CONSTRAINT pk_staticpublishrecord PRIMARY KEY (id)
);

CREATE INDEX name_idx ON static_publish_record (site_name);

CREATE INDEX uid_idx ON static_publish_record (uid);

CREATE INDEX user_name_idx ON static_publish_record (username, site_name);