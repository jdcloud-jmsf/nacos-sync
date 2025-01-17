/******************************************/
/*   DB name = nacos_sync   */
/*   Table name = cluster   */
/******************************************/
CREATE TABLE `cluster` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `cluster_id` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `cluster_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `cluster_type` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `connect_key_list` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `user_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `namespace` VARCHAR(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `mesh_id` VARCHAR(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `cluster_level` int default 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/******************************************/
/*   DB name = nacos_sync   */
/*   Table name = system_config   */
/******************************************/
CREATE TABLE `system_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `config_desc` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `config_key` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `config_value` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/******************************************/
/*   DB name = nacos_sync   */
/*   Table name = task   */
/******************************************/
CREATE TABLE `task` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `dest_cluster_id` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `group_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `name_space` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `operation_id` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `service_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `source_cluster_id` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `task_id` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `task_status` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `version` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `worker_ip` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `status` int default null ,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `metadata`
(
    `meta_key`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `meta_value`      varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `version`         varchar(255) COLLATE utf8mb4_bin                        DEFAULT NULL,
    `expiration_time` bigint                                                  DEFAULT NULL,
    PRIMARY KEY (`meta_key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `cluster_task`
(
    `id`                int NOT NULL AUTO_INCREMENT,
    `dest_cluster_id`   varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `group_name`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `name_space`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `operation_id`      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `source_cluster_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `task_id`           varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `task_status`       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
    `version`           varchar(255) COLLATE utf8mb4_bin                       DEFAULT NULL,
    `cluster_task_id`   varchar(255) COLLATE utf8mb4_bin                       DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;