package com.alibaba.nacossync.jmsf.constant;

public class JmsfConstants {

    /**
     * nacos-sync同步相关注解
     */
    public final static String DEST_CLUSTER_ANNOTATION = "destClusterId";
    public final static String SOURCE_CLUSTER_ANNOTATION = "sourceClusterId";
    public final static String SYNC_TASK_ID_ANNOTATION = "sync-task-id";
    public final static String SYNC_SOURCE_ANNOTATION = "syncSource";

    /**
     * 注册中心类型
     */
    public final static String NACOS_SYNC_SOURCE = "NACOS";
    public final static String EUREKA_SYNC_SOURCE = "EUREKA";
    public final static String CONSUL_SYNC_SOURCE = "CONSUL";

    /**
     * msi的实例状态
     */
    public final static String ONLINE_STATUS = "Online";
    public final static String HANG_UP_STATUS = "HangUp";
    public final static String HANG_UP_SYNC_STATUS = "HangUpSyncing";

    /**
     * Metadata
     */
    public final static String CLUSTER_LEADER = "cluster_leader";
}
