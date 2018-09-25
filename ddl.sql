DROP TABLE IF EXISTS `cookie`;

CREATE TABLE `cookie` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `value` varchar(4096) NOT NULL,
  `service` tinyint(1) NOT NULL,
  `application` tinyint(1) NOT NULL,
  `phone` varchar(11) DEFAULT NULL,
  `open_id` varchar(64) NOT NULL,
  `nickname` varchar(128) DEFAULT NULL,
  `head_img_url` varchar(512) DEFAULT NULL,
  `is_valid` tinyint(1) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `gmt_create` datetime NOT NULL,
  `gmt_modified` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_open_id` (`open_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP TABLE IF EXISTS `cookie_use_count`;

CREATE TABLE `cookie_use_count` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `status` tinyint(1) NOT NULL,
  `application` tinyint(1) NOT NULL,
  `open_id` varchar(64) NOT NULL,
  `cookie_id` bigint(20) NOT NULL,
  `cookie_user_id` bigint(20) NOT NULL,
  `receiving_id` bigint(20) NOT NULL,
  `receiving_user_id` bigint(20) NOT NULL,
  `gmt_create` datetime NOT NULL,
  `gmt_modified` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_open_id` (`open_id`),
  KEY `idx_cookie_id` (`cookie_id`),
  KEY `idx_cookie_user_id` (`cookie_user_id`),
  KEY `idx_receiving_id` (`receiving_id`),
  KEY `idx_receiving_user_id` (`receiving_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP TABLE IF EXISTS `receiving`;

CREATE TABLE `receiving` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `url_key` varchar(32) NOT NULL,
  `url` varchar(512) NOT NULL,
  `phone` varchar(11) NOT NULL,
  `application` tinyint(1) NOT NULL,
  `type` tinyint(1) DEFAULT NULL,
  `status` tinyint(1) NOT NULL,
  `message` varchar(512) DEFAULT NULL,
  `nickname` varchar(256) DEFAULT NULL,
  `price` decimal(5,2) DEFAULT NULL,
  `date` varchar(64) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  `gmt_create` datetime NOT NULL,
  `gmt_modified` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_url_key` (`url_key`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `avatar` varchar(256) DEFAULT NULL,
  `name` varchar(32) DEFAULT NULL,
  `password` char(128) NOT NULL,
  `salt` char(32) NOT NULL,
  `mail` varchar(128) DEFAULT NULL,
  `phone` varchar(11) DEFAULT NULL,
  `token` char(128) NOT NULL,
  `is_locked` tinyint(1) NOT NULL,
  `gmt_create` datetime NOT NULL,
  `gmt_modified` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  UNIQUE KEY `uk_mail` (`mail`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP TABLE IF EXISTS `verification`;

CREATE TABLE `verification` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `object` varchar(512) NOT NULL,
  `type` tinyint(1) NOT NULL,
  `code` char(128) NOT NULL,
  `purpose` tinyint(1) NOT NULL,
  `is_used` tinyint(1) NOT NULL,
  `gmt_create` datetime NOT NULL,
  `gmt_modified` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

