/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacossync.timer;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacossync.cache.SkyWalkerCacheServices;
import com.alibaba.nacossync.constant.MetricsStatisticsType;
import com.alibaba.nacossync.constant.TaskStatusEnum;
import com.alibaba.nacossync.dao.ClusterTaskAccessService;
import com.alibaba.nacossync.dao.MetadataAccessService;
import com.alibaba.nacossync.dao.TaskAccessService;
import com.alibaba.nacossync.event.DeleteTaskEvent;
import com.alibaba.nacossync.event.SyncTaskEvent;
import com.alibaba.nacossync.jmsf.constant.JmsfConstants;
import com.alibaba.nacossync.monitor.MetricsManager;
import com.alibaba.nacossync.pojo.model.ClusterTaskDO;
import com.alibaba.nacossync.pojo.model.MetadataDO;
import com.alibaba.nacossync.pojo.model.TaskDO;
import com.alibaba.nacossync.pojo.request.TaskAddAllRequest;
import com.alibaba.nacossync.template.processor.TaskAddAllProcessor;
import com.alibaba.nacossync.util.SkyWalkerUtil;
import com.google.common.eventbus.EventBus;
import io.meshware.common.timer.ScheduleTask;
import io.meshware.common.timer.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * QuerySyncClusterTaskTimer
 *
 * @author Zhiguo.Chen
 * @since 20230206
 */
@Slf4j
@Service
public class QuerySyncClusterTaskTimer implements CommandLineRunner {
    @Autowired
    private MetricsManager metricsManager;

    @Autowired
    private SkyWalkerCacheServices skyWalkerCacheServices;

    @Autowired
    private ClusterTaskAccessService clusterTaskAccessService;

    @Autowired
    private MetadataAccessService metadataAccessService;

    @Autowired
    private TaskAccessService taskAccessService;

    @Autowired
    private TaskAddAllProcessor taskAddAllProcessor;

    @Override
    public void run(String... args) {
        TimerHolder.timer.add(new RefreshClusterTask(TimerHolder.timer));
    }

    private class RefreshClusterTask extends ScheduleTask {

        public RefreshClusterTask(Timer currentTimer) {
            super(currentTimer);
        }

        @Override
        public CompletableFuture<Void> execute() {
            CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            Long start = System.currentTimeMillis();
            try {
                MetadataDO metadataDO = metadataAccessService.getMetadataByKey(JmsfConstants.CLUSTER_LEADER);
                if (Objects.isNull(metadataDO)) {
                    metadataDO = new MetadataDO();
                    metadataDO.setMetaKey(JmsfConstants.CLUSTER_LEADER);
                    metadataDO.setMetaValue(SkyWalkerUtil.getLocalIp());
                    metadataDO.setVersion(MD5Utils.encodeHexString(metadataDO.getMetaValue().getBytes(StandardCharsets.UTF_8)));
                    metadataDO.setExpirationTime(System.currentTimeMillis() + 10000);
                    metadataAccessService.saveMetadataByKey(metadataDO);
                } else if (Objects.isNull(metadataDO.getExpirationTime()) || metadataDO.getExpirationTime() < System.currentTimeMillis()
                        || SkyWalkerUtil.getLocalIp().equals(metadataDO.getMetaValue())) {
                    metadataDO.setMetaValue(SkyWalkerUtil.getLocalIp());
                    metadataDO.setVersion(MD5Utils.encodeHexString(metadataDO.getMetaValue().getBytes(StandardCharsets.UTF_8)));
                    metadataDO.setExpirationTime(System.currentTimeMillis() + 10000);
                    metadataAccessService.saveMetadataByKey(metadataDO);
                } else {
                    completableFuture.complete(null);
                    return completableFuture;
                }
                Iterable<ClusterTaskDO> clusterTaskDOS = clusterTaskAccessService.findAll();
                clusterTaskDOS.forEach(taskDO -> {
                    if (TaskStatusEnum.SYNC.getCode().equals(taskDO.getTaskStatus())) {
                        TaskAddAllRequest addAllRequest = new TaskAddAllRequest();
                        addAllRequest.setSourceClusterId(taskDO.getSourceClusterId());
                        addAllRequest.setDestClusterId(taskDO.getDestClusterId());
                        try {
                            taskAddAllProcessor.process(addAllRequest, null);
                        } catch (Exception e) {
                            log.error("Refresh cluster task error! message:{}", e.getMessage(), e);
                        }
                    }
                    if (TaskStatusEnum.DELETE.getCode().equals(taskDO.getTaskStatus())) {
                        List<TaskDO> taskDOList = taskAccessService.findByClusterTaskId(taskDO.getClusterTaskId());
                        for (TaskDO task : taskDOList) {
                            task.setTaskStatus(TaskStatusEnum.DELETE.getCode());
                            taskAccessService.addTask(task);
                        }
                    }
                });
            } catch (Exception e) {
                log.warn("RefreshClusterTask exception", e);
            }
            metricsManager.record(MetricsStatisticsType.DISPATCHER_TASK, System.currentTimeMillis() - start);
            completableFuture.complete(null);
            return completableFuture;
        }

        @Override
        public int getIntervalInMs() {
            return 10000;
        }

        @Override
        public boolean isAsync() {
            return false;
        }

        @Override
        public String getName() {
            return "refresh-cluster-task";
        }
    }
}
