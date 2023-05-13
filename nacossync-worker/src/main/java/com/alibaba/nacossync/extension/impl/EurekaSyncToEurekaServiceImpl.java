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
import com.alibaba.nacossync.extension.eureka.EurekaNamingService;
import com.alibaba.nacossync.extension.event.SpecialSyncEventBus;
import com.alibaba.nacossync.extension.holder.EurekaServerHolder;
import com.alibaba.nacossync.monitor.MetricsManager;
import com.alibaba.nacossync.pojo.model.TaskDO;
import com.netflix.appinfo.InstanceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * eureka2eureka
 *
 * @author Zhiguo.Chen
 */
@Slf4j
@NacosSyncService(sourceCluster = ClusterTypeEnum.EUREKA, destinationCluster = ClusterTypeEnum.EUREKA)
public class EurekaSyncToEurekaServiceImpl implements SyncService {

    private final MetricsManager metricsManager;

    private final EurekaServerHolder eurekaServerHolder;
    private final SkyWalkerCacheServices skyWalkerCacheServices;

    private final SpecialSyncEventBus specialSyncEventBus;

    @Autowired
    public EurekaSyncToEurekaServiceImpl(EurekaServerHolder eurekaServerHolder,
                                         SkyWalkerCacheServices skyWalkerCacheServices,
                                         SpecialSyncEventBus specialSyncEventBus, MetricsManager metricsManager) {
        this.eurekaServerHolder = eurekaServerHolder;
        this.skyWalkerCacheServices = skyWalkerCacheServices;
        this.specialSyncEventBus = specialSyncEventBus;
        this.metricsManager = metricsManager;
    }

    @Override
    public boolean delete(TaskDO taskDO) {
        try {
            specialSyncEventBus.unsubscribe(taskDO);
            EurekaNamingService eurekaNamingService = eurekaServerHolder.get(taskDO.getSourceClusterId());
            EurekaNamingService destEurekaNamingService = eurekaServerHolder.get(taskDO.getDestClusterId());
            List<InstanceInfo> eurekaInstances = eurekaNamingService.getApplications(taskDO.getServiceName());
            deleteAllInstanceFromEureka(taskDO, destEurekaNamingService, eurekaInstances);
        } catch (Exception e) {
            log.error("delete a task from eureka to eureka was failed, taskId:{}", taskDO.getTaskId(), e);
            metricsManager.recordError(MetricsStatisticsType.DELETE_ERROR);
            return false;
        }
        return true;
    }

    @Override
    public boolean sync(TaskDO taskDO, Integer index) {
        try {
            EurekaNamingService eurekaNamingService = eurekaServerHolder.get(taskDO.getSourceClusterId());
            EurekaNamingService destEurekaNamingService = eurekaServerHolder.get(taskDO.getDestClusterId());
            List<InstanceInfo> eurekaInstances = eurekaNamingService.getApplications(taskDO.getServiceName());
            List<InstanceInfo> destEurekaInstances = destEurekaNamingService.getApplications(taskDO.getServiceName());
            if (CollectionUtils.isEmpty(eurekaInstances)) {
                // Clear all instance from Nacos
                deleteAllInstanceFromEureka(taskDO, destEurekaNamingService, destEurekaInstances);
            } else {
                if (!CollectionUtils.isEmpty(destEurekaInstances)) {
                    // Remove invalid instance from Nacos
                    removeInvalidInstance(taskDO, destEurekaNamingService, eurekaInstances, destEurekaInstances);
                }
                addValidInstance(taskDO, destEurekaNamingService, eurekaInstances);
            }
            specialSyncEventBus.subscribe(taskDO, t -> syncTask(t, index));
        } catch (Exception e) {
            log.error("sync task from eureka to eureka was failed, taskId:{}", taskDO.getTaskId(), e);
            metricsManager.recordError(MetricsStatisticsType.SYNC_ERROR);
            return false;
        }
        return true;
    }

    private boolean syncTask(TaskDO taskDO, Integer index) {
        try {
            EurekaNamingService eurekaNamingService = eurekaServerHolder.get(taskDO.getSourceClusterId());
            EurekaNamingService destEurekaNamingService = eurekaServerHolder.get(taskDO.getDestClusterId());
            List<InstanceInfo> eurekaInstances = eurekaNamingService.getApplications(taskDO.getServiceName());
            List<InstanceInfo> destEurekaInstances = destEurekaNamingService.getApplications(taskDO.getServiceName());
            if (CollectionUtils.isEmpty(eurekaInstances)) {
                // Clear all instance from Nacos
                deleteAllInstanceFromEureka(taskDO, destEurekaNamingService, destEurekaInstances);
            } else {
                if (!CollectionUtils.isEmpty(destEurekaInstances)) {
                    // Remove invalid instance from Nacos
                    removeInvalidInstance(taskDO, destEurekaNamingService, eurekaInstances, destEurekaInstances);
                }
                addValidInstance(taskDO, destEurekaNamingService, eurekaInstances);
            }
        } catch (Exception e) {
            log.error("sync task from eureka to eureka was failed, taskId:{}", taskDO.getTaskId(), e);
            metricsManager.recordError(MetricsStatisticsType.SYNC_ERROR);
            return false;
        }
        return true;
    }

    private void addValidInstance(TaskDO taskDO, EurekaNamingService destEurekaNamingService, List<InstanceInfo> eurekaInstances) {
        for (InstanceInfo instance : eurekaInstances) {
            if (needSync(instance.getMetadata())) {
                log.info("Add service instance from Eureka, serviceName={}, Ip={}, port={}",
                        instance.getAppName(), instance.getIPAddr(), instance.getPort());
                destEurekaNamingService.registerInstance(buildSyncInstance(instance, taskDO));
            }
        }
    }

    private void deleteAllInstanceFromEureka(TaskDO taskDO, EurekaNamingService destEurekaNamingService,
                                             List<InstanceInfo> eurekaInstances) {
        if (CollectionUtils.isEmpty(eurekaInstances)) {
            return;
        }
        for (InstanceInfo instance : eurekaInstances) {
            if (needSync(instance.getMetadata())) {
                log.info("Delete service instance from Eureka, serviceName={}, Ip={}, port={}",
                        instance.getAppName(), instance.getIPAddr(), instance.getPort());
                destEurekaNamingService.deregisterInstance(instance);
            }
        }
    }

    private void removeInvalidInstance(TaskDO taskDO, EurekaNamingService destEurekaNamingService,
                                       List<InstanceInfo> eurekaInstances, List<InstanceInfo> destEurekaInstances) {
        for (InstanceInfo instance : destEurekaInstances) {
            if (!isExistInEurekaInstance(eurekaInstances, instance) && needDelete(instance.getMetadata(), taskDO)) {
                log.info("Remove invalid service instance from Eureka, serviceName={}, Ip={}, port={}",
                        instance.getAppName(), instance.getIPAddr(), instance.getPort());
                destEurekaNamingService.deregisterInstance(instance);
            }
        }
    }

    private boolean isExistInEurekaInstance(List<InstanceInfo> eurekaInstances, InstanceInfo instanceInfo) {
        return eurekaInstances.stream().anyMatch(instance -> instance.getIPAddr().equals(instanceInfo.getIPAddr())
                && instance.getPort() == instanceInfo.getPort());
    }

    private InstanceInfo buildSyncInstance(InstanceInfo instance, TaskDO taskDO) {
        Map<String, String> metaData = instance.getMetadata();
        metaData.put(SkyWalkerConstants.DEST_CLUSTERID_KEY, taskDO.getDestClusterId());
        metaData.put(SkyWalkerConstants.SYNC_SOURCE_KEY,
                skyWalkerCacheServices.getClusterType(taskDO.getSourceClusterId()).getCode());
        metaData.put(SkyWalkerConstants.SOURCE_CLUSTERID_KEY, taskDO.getSourceClusterId());
        return instance;
    }

}
