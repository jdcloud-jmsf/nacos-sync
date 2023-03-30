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

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacossync.cache.SkyWalkerCacheServices;
import com.alibaba.nacossync.constant.ClusterTypeEnum;
import com.alibaba.nacossync.constant.MetricsStatisticsType;
import com.alibaba.nacossync.constant.SkyWalkerConstants;
import com.alibaba.nacossync.extension.SyncService;
import com.alibaba.nacossync.extension.annotation.NacosSyncService;
import com.alibaba.nacossync.extension.holder.ConsulServerHolder;
import com.alibaba.nacossync.extension.holder.NacosServerHolder;
import com.alibaba.nacossync.monitor.MetricsManager;
import com.alibaba.nacossync.pojo.model.TaskDO;
import com.alibaba.nacossync.util.ConsulUtils;
import com.alibaba.nacossync.util.NacosUtils;
import com.alibaba.nacossync.util.StringUtils;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.health.model.HealthService;
import com.google.common.collect.Lists;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * @author zhanglong
 */
@Slf4j
@NacosSyncService(sourceCluster = ClusterTypeEnum.NACOS, destinationCluster = ClusterTypeEnum.CONSUL)
public class NacosSyncToConsulServiceImpl implements SyncService {

    private Map<String, EventListener> nacosListenerMap = new ConcurrentHashMap<>();

    private final MetricsManager metricsManager;

    private final SkyWalkerCacheServices skyWalkerCacheServices;

    private final NacosServerHolder nacosServerHolder;
    private final ConsulServerHolder consulServerHolder;

    public NacosSyncToConsulServiceImpl(MetricsManager metricsManager, SkyWalkerCacheServices skyWalkerCacheServices,
                                        NacosServerHolder nacosServerHolder, ConsulServerHolder consulServerHolder) {
        this.metricsManager = metricsManager;
        this.skyWalkerCacheServices = skyWalkerCacheServices;
        this.nacosServerHolder = nacosServerHolder;
        this.consulServerHolder = consulServerHolder;
    }

    @Override
    public boolean delete(TaskDO taskDO) {
        try {

            NamingService sourceNamingService =
                    nacosServerHolder.get(taskDO.getSourceClusterId());
            ConsulClient consulClient = consulServerHolder.get(taskDO.getDestClusterId());

            sourceNamingService.unsubscribe(taskDO.getServiceName(),
                    NacosUtils.getGroupNameOrDefault(taskDO.getGroupName()), nacosListenerMap.get(taskDO.getTaskId()));

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
            log.error("delete a task from nacos to nacos was failed, taskId:{}", taskDO.getTaskId(), e);
            metricsManager.recordError(MetricsStatisticsType.DELETE_ERROR);
            return false;
        }
        return true;
    }

    @Override
    public boolean sync(TaskDO taskDO) {
        try {
            NamingService sourceNamingService = nacosServerHolder.get(taskDO.getSourceClusterId());
            if (Objects.isNull(sourceNamingService)) {
                log.error("未获取到该注册中心对应的client！clusterId={}", taskDO.getSourceClusterId());
                return false;
            }
            ConsulClient consulClient = consulServerHolder.get(taskDO.getDestClusterId());
            if (Objects.isNull(consulClient)) {
                log.error("未获取到该注册中心对应的client！clusterId={}", taskDO.getDestClusterId());
                return false;
            }
            log.info("开始处理同步任务，taskDO={}", taskDO);
            nacosListenerMap.putIfAbsent(taskDO.getTaskId(), event -> {
                if (event instanceof NamingEvent) {
                    try {
                        log.info("接收到注册中心的事件，taskDO={}，serviceName={}", taskDO, ((NamingEvent) event).getServiceName());
                        Set<String> instanceKeySet = new HashSet<>();
                        List<Instance> sourceInstances = sourceNamingService.getAllInstances(taskDO.getServiceName(),
                                NacosUtils.getGroupNameOrDefault(taskDO.getGroupName()));
                        // 先将新的注册一遍
                        log.info("The number of instances of the service({}) from nacos is:{}", taskDO.getServiceName(), sourceInstances.size());
                        for (Instance instance : sourceInstances) {
                            if (needSync(instance.getMetadata())) {
                                consulClient.agentServiceRegister(buildSyncInstance(instance, taskDO),
                                        ConsulServerHolder.getToken(taskDO.getDestClusterId()));
                                instance.getInstanceId();
                                instanceKeySet.add(composeInstanceKey(instance.getIp(), instance.getPort()));
                            }
                        }

                        // 再将不存在的删掉
                        Response<List<HealthService>> serviceResponse =
                                consulClient.getHealthServices(taskDO.getServiceName(), true, QueryParams.DEFAULT,
                                        ConsulServerHolder.getToken(taskDO.getDestClusterId()));
                        List<HealthService> healthServices = serviceResponse.getValue();
                        log.info("The number of instances of the service({}) from consul is:{}", taskDO.getServiceName(), healthServices.size());
                        for (HealthService healthService : healthServices) {

                            if (needDelete(ConsulUtils.transferMetadata(healthService.getService().getTags()), taskDO)
                                    && !instanceKeySet.contains(composeInstanceKey(healthService.getService().getAddress(),
                                    healthService.getService().getPort()))) {
                                consulClient.agentServiceDeregister(URLEncoder
                                                .encode(healthService.getService().getId(), StandardCharsets.UTF_8.toString()),
                                        ConsulServerHolder.getToken(taskDO.getDestClusterId()));
                            }
                        }
                    } catch (Exception e) {
                        log.error("event process fail, taskId:{}", taskDO.getTaskId(), e);
                        metricsManager.recordError(MetricsStatisticsType.SYNC_ERROR);
                    }
                }
            });
            // 先触发一次同步
            log.info("开始处理同步任务，先触发一次同步，taskDO={}", taskDO);
            nacosListenerMap.get(taskDO.getTaskId()).onEvent(new NamingEvent(taskDO.getServiceName(), null));
            sourceNamingService.subscribe(taskDO.getServiceName(),
                    NacosUtils.getGroupNameOrDefault(taskDO.getGroupName()),
                    nacosListenerMap.get(taskDO.getTaskId()));
        } catch (Exception e) {
            log.error("sync task from nacos to consul was failed, taskId:{}", taskDO.getTaskId(), e);
            metricsManager.recordError(MetricsStatisticsType.SYNC_ERROR);
            return false;
        }
        return true;
    }

    private String composeInstanceKey(String ip, int port) {
        return ip + ":" + port;
    }

    public NewService buildSyncInstance(Instance instance, TaskDO taskDO) {
        NewService newService = new NewService();
        newService.setAddress(instance.getIp());
        newService.setPort(instance.getPort());
        newService.setName(taskDO.getServiceName());
        if (!StringUtils.isEmpty(instance.getInstanceId())) {
            newService.setId(instance.getInstanceId());
        } else {
            newService.setId(taskDO.getServiceName() + "-" + instance.getIp() + "-" + instance.getPort());
        }
        List<String> tags = Lists.newArrayList();
        tags.addAll(instance.getMetadata().entrySet().stream()
                .map(entry -> String.join("=", entry.getKey(), entry.getValue())).collect(Collectors.toList()));
        tags.add(String.join("=", SkyWalkerConstants.DEST_CLUSTERID_KEY, taskDO.getDestClusterId()));
        tags.add(String.join("=", SkyWalkerConstants.SYNC_SOURCE_KEY,
                skyWalkerCacheServices.getClusterType(taskDO.getSourceClusterId()).getCode()));
        tags.add(String.join("=", SkyWalkerConstants.SOURCE_CLUSTERID_KEY, taskDO.getSourceClusterId()));
        newService.setTags(tags);
        Map<String, String> meta = convertMeta(instance.getMetadata());
        meta.put("sync-task-id", taskDO.getTaskId());
        newService.setMeta(meta);
        // newService.setCheck(new NewService.Check());
        return newService;
    }

    private Map<String, String> convertMeta(Map<String, String> metadata) {
        Map<String, String> meta = new HashMap<>(metadata.size());
        metadata.forEach((s, s2) -> {
            if (s.contains(".")) {
                s = s.replace(".", "-");
            }
            meta.put(s, s2);
        });
        return meta;
    }

}
