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

import com.alibaba.nacossync.cache.SkyWalkerCacheServices;
import com.alibaba.nacossync.constant.MetricsStatisticsType;
import com.alibaba.nacossync.constant.TaskStatusEnum;
import com.alibaba.nacossync.dao.ClusterTaskAccessService;
import com.alibaba.nacossync.dao.MetadataAccessService;
import com.alibaba.nacossync.dao.TaskAccessService;
import com.alibaba.nacossync.monitor.MetricsManager;
import com.alibaba.nacossync.pojo.model.ClusterTaskDO;
import com.alibaba.nacossync.pojo.model.TaskDO;
import com.alibaba.nacossync.pojo.request.TaskAddAllRequest;
import com.alibaba.nacossync.template.processor.TaskAddAllProcessor;
import com.alibaba.nacossync.util.SkyWalkerUtil;
import io.meshware.common.timer.ScheduleTask;
import io.meshware.common.timer.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
                if (!CampaignTaskTimer.IS_LEADER.get()) {
                    completableFuture.complete(null);
                    log.info("The current node is a standby node and does not perform cluster task processing.");
                    return completableFuture;
                } else {
                    log.info("The current node is a work node and does perform cluster task processing.");
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
                            String oldOperationId = task.getOperationId();
                            task.setOperationId(SkyWalkerUtil.generateOperationId());
                            task.setTaskStatus(TaskStatusEnum.DELETE.getCode());
                            taskAccessService.addTask(task);
                            skyWalkerCacheServices.removeFinishedTask(oldOperationId);
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
            return "query-sync-cluster-task";
        }
    }
}
