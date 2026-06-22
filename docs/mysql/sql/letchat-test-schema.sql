SET NAMES utf8mb4;

DROP TABLE IF EXISTS app_update;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session_user;
DROP TABLE IF EXISTS chat_session;
DROP TABLE IF EXISTS group_info;
DROP TABLE IF EXISTS user_contact_apply;
DROP TABLE IF EXISTS user_contact;
DROP TABLE IF EXISTS user_info_beauty;
DROP TABLE IF EXISTS user_info;

CREATE TABLE user_info (
  user_id varchar(20) NOT NULL,
  email varchar(128) NOT NULL,
  nick_name varchar(64) NOT NULL,
  join_type int DEFAULT 1,
  sex int DEFAULT NULL,
  password varchar(64) NOT NULL,
  personal_signature varchar(255) DEFAULT NULL,
  status int DEFAULT 1,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  last_login_time datetime DEFAULT NULL,
  area_name varchar(64) DEFAULT NULL,
  area_code varchar(32) DEFAULT NULL,
  last_off_time bigint DEFAULT 0,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_email (email),
  KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_info_beauty (
  id int NOT NULL AUTO_INCREMENT,
  email varchar(128) DEFAULT NULL,
  user_id varchar(20) DEFAULT NULL,
  status int DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_beauty_email (email),
  UNIQUE KEY uk_beauty_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_contact (
  user_id varchar(20) NOT NULL,
  contact_id varchar(20) NOT NULL,
  contact_type int NOT NULL,
  contact_name varchar(128) DEFAULT NULL,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  status int DEFAULT 1,
  last_update_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, contact_id),
  KEY idx_contact_id (contact_id),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_contact_apply (
  apply_id int NOT NULL AUTO_INCREMENT,
  apply_user_id varchar(20) NOT NULL,
  receive_user_id varchar(20) NOT NULL,
  contact_type int NOT NULL,
  contact_id varchar(20) NOT NULL,
  last_apply_time bigint DEFAULT NULL,
  status int DEFAULT 0,
  apply_info varchar(255) DEFAULT NULL,
  PRIMARY KEY (apply_id),
  KEY idx_receive_status_time (receive_user_id, status, last_apply_time),
  KEY idx_apply_contact (apply_user_id, receive_user_id, contact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE group_info (
  group_id varchar(20) NOT NULL,
  group_name varchar(128) NOT NULL,
  group_owner_id varchar(20) NOT NULL,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  group_notice varchar(1024) DEFAULT NULL,
  join_type int DEFAULT 1,
  status int DEFAULT 1,
  PRIMARY KEY (group_id),
  KEY idx_group_owner (group_owner_id),
  KEY idx_group_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_session (
  session_id varchar(64) NOT NULL,
  last_message varchar(500) DEFAULT NULL,
  last_receive_time bigint DEFAULT NULL,
  PRIMARY KEY (session_id),
  KEY idx_last_receive_time (last_receive_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_session_user (
  user_id varchar(20) NOT NULL,
  contact_id varchar(20) NOT NULL,
  session_id varchar(64) NOT NULL,
  contact_name varchar(128) DEFAULT NULL,
  PRIMARY KEY (user_id, contact_id),
  KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_message (
  message_id bigint NOT NULL AUTO_INCREMENT,
  session_id varchar(64) DEFAULT NULL,
  message_type int DEFAULT NULL,
  message_content varchar(1000) DEFAULT NULL,
  send_user_id varchar(20) DEFAULT NULL,
  send_user_nick_name varchar(64) DEFAULT NULL,
  send_time bigint DEFAULT NULL,
  contact_id varchar(20) DEFAULT NULL,
  contact_type int DEFAULT NULL,
  file_size bigint DEFAULT NULL,
  file_name varchar(255) DEFAULT NULL,
  file_type int DEFAULT NULL,
  status int DEFAULT NULL,
  PRIMARY KEY (message_id),
  UNIQUE KEY uk_message_fingerprint (session_id, send_user_id, contact_id, send_time, message_type),
  KEY idx_session_time (session_id, send_time),
  KEY idx_contact_time (contact_id, send_time),
  KEY idx_send_contact (send_user_id, contact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_update (
  id int NOT NULL AUTO_INCREMENT,
  version varchar(32) NOT NULL,
  update_desc varchar(1024) DEFAULT NULL,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  status int DEFAULT 0,
  grayscale_uid varchar(255) DEFAULT NULL,
  file_type int DEFAULT 0,
  outer_link varchar(512) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
