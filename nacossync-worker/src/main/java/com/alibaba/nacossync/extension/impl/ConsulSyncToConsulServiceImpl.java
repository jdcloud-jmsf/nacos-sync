/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.nacossync.extension.impl;

import com.alibaba.nacossync.cache.SkyWalkerCacheServices;
import com.alibaba.nacossync.constant.ClusterTypeEnum;
import com.alibaba.nacossync.constant.MetricsStatisticsType;
import com.alibaba.nacossync.constant.SkyWalkerConstants;
import com.alibaba.nacossync.extension.SyncService;
import com.alibaba.nacossync.extension.annotation.NacosSyncService;
import com.alibaba.nacossync.extension.event.SpecialSyncEventBus;
import com.alibaba.nacossync.extension.holder.ConsulServerHolder;
import com.alibaba.nacossync.monitor.MetricsManager;
import com.alibaba.nacossync.pojo.model.TaskDO;
import com.alibaba.nacossync.util.ConsulUtils;
import com.alibaba.nacossync.util.StringUtils;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.health.model.HealthService;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Consul 同步 Consul
 *
 * @author Zhiguo.Chen
 */
@Slf4j
@NacosSyncService(sourceCluster = ClusterTypeEnum.CONSUL, destinationCluster = ClusterTypeEnum.CONSUL)
public class ConsulSyncToConsulServiceImpl implements SyncService {

    @Autowired
    private MetricsManager metricsManager;

    private final ConsulServerHolder consulServerHolder;
    private final SkyWalkerCacheServices skyWalkerCacheServices;
    private final SpecialSyncEventBus specialSyncEventBus;

    @Autowired
    public ConsulSyncToConsulServiceImpl(ConsulServerHolder consulServerHolder,
                                         SkyWalkerCacheServices skyWalkerCacheServices,
                                         SpecialSyncEventBus specialSyncEventBus) {
        this.consulServerHolder = consulServerHolder;
        this.skyWalkerCacheServices = skyWalkerCacheServices;
        this.specialSyncEventBus = specialSyncEventBus;
    }

    @Override
    public boolean delete(TaskDO taskDO) {
        try {
            specialSyncEventBus.unsubscribe(taskDO);

            ConsulClient consulClient = consulServerHolder.get(taskDO.getDestClusterId());
            // 删除目标集群中同步的实例列表
            Response<List<HealthService>> serviceResponse =
                    consulClient.getHealthServices(taskDO.getServiceName(), true, QueryParams.DEFAULT,
                            ConsulServerHolder.getToken(taskDO.getDestClusterId()));
            List<HealthService> healthServices = serviceResponse.getValue();
            for (HealthService healthService : healthServices) {
                if (needDelete(ConsulUtils.transferMetadata(healthService.getService().getTags()), taskDO)) {
                    consulClient.agentServiceDeregister(URLEncoder
                                    .encode(healthService.getService().getId(), StandardCharsets.UTF_8.name()),
                            ConsulServerHolder.getToken(taskDO.getDestClusterId()));
                }
            }
        } catch (Exception e) {
            log.error("delete task from consul to nacos was failed, taskId:{}", taskDO.getTaskId(), e);
            metricsManager.recordError(MetricsStatisticsType.DELETE_ERROR);
            return false;
        }
        return true;
    }

    @Override
    public boolean sync(TaskDO taskDO, Integer index) {
        try {
            ConsulClient consulClient = consulServerHolder.get(taskDO.getSourceClusterId());
            ConsulClient consulClient2 = consulServerHolder.get(taskDO.getDestClusterId());
            Response<List<HealthService>> response =
                    consulClient.getHealthServices(taskDO.getServiceName(), true, QueryParams.DEFAULT,
                            ConsulServerHolder.getToken(taskDO.getSourceClusterId()));
            List<HealthService> healthServiceList = response.getValue();
            Set<String> instanceKeys = new HashSet<>();
            overrideAllInstance(taskDO, consulClient2, healthServiceList, instanceKeys);
            cleanAllOldInstance(taskDO, consulClient2, instanceKeys);
            specialSyncEventBus.subscribe(taskDO, t->sync(t, index));
        } catch (Exception e) {
            log.error("Sync task from consul to nacos was failed, taskId:{}", taskDO.getTaskId(), e);
            metricsManager.recordError(MetricsStatisticsType.SYNC_ERROR);
            return false;
        }
        return true;
    }

    private void cleanAllOldInstance(TaskDO taskDO, ConsulClient consulClient, Set<String> instanceKeys)
            throws Exception {
        // 再将不存在的删掉
        Response<List<HealthService>> serviceResponse =
                consulClient.getHealthServices(taskDO.getServiceName(), true, QueryParams.DEFAULT,
                        ConsulServerHolder.getToken(taskDO.getDestClusterId()));
        List<HealthService> healthServices = serviceResponse.getValue();
        for (HealthService healthService : healthServices) {
            if (needDelete(ConsulUtils.transferMetadata(healthService.getService().getTags()), taskDO)
                    && !instanceKeys.contains(composeInstanceKey(healthService.getService().getAddress(),
                    healthService.getService().getPort()))) {
                consulClient.agentServiceDeregister(URLEncoder
                                .encode(healthService.getService().getId(), StandardCharsets.UTF_8.toString()),
                        ConsulServerHolder.getToken(taskDO.getDestClusterId()));
            }
        }
    }

    private void overrideAllInstance(TaskDO taskDO, ConsulClient consulClient,
                                     List<HealthService> healthServiceList, Set<String> instanceKeys) throws Exception {
        for (HealthService healthService : healthServiceList) {
            if (needSync(ConsulUtils.transferMetadata(healthService.getService().getTags()))) {
                consulClient.agentServiceRegister(buildSyncInstance(healthService, taskDO),
                        ConsulServerHolder.getToken(taskDO.getDestClusterId()));

                instanceKeys.add(composeInstanceKey(healthService.getService().getAddress(),
                        healthService.getService().getPort()));
            }
        }
    }

    public NewService buildSyncInstance(HealthService instance, TaskDO taskDO) {
        NewService newService = new NewService();
        newService.setAddress(instance.getService().getAddress());
        newService.setPort(instance.getService().getPort());
        newService.setName(taskDO.getServiceName());
        if (!StringUtils.isEmpty(instance.getService().getId())) {
            newService.setId(instance.getService().getId());
        } else {
            newService.setId(taskDO.getServiceName() + "-" + instance.getService().getAddress() + "-" + instance.getService().getPort());
        }
        List<String> tags = Lists.newArrayList();
        tags.addAll(instance.getService().getTags());
        tags.add(String.join("=", SkyWalkerConstants.DEST_CLUSTERID_KEY, taskDO.getDestClusterId()));
        tags.add(String.join("=", SkyWalkerConstants.SYNC_SOURCE_KEY,
                skyWalkerCacheServices.getClusterType(taskDO.getSourceClusterId()).getCode()));
        tags.add(String.join("=", SkyWalkerConstants.SOURCE_CLUSTERID_KEY, taskDO.getSourceClusterId()));
        newService.setTags(tags);
        newService.setMeta(instance.getService().getMeta());
        // newService.setCheck(new NewService.Check());
        return newService;
    }

    private String composeInstanceKey(String ip, int port) {
        return ip + ":" + port;
    }

}
