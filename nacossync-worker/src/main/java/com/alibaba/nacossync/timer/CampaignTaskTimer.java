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
import com.alibaba.nacossync.constant.SkyWalkerConstants;
import com.alibaba.nacossync.dao.MetadataAccessService;
import com.alibaba.nacossync.pojo.model.MetadataDO;
import com.alibaba.nacossync.util.SkyWalkerUtil;
import io.meshware.common.timer.ScheduleTask;
import io.meshware.common.timer.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CampaignTaskTimer
 *
 * @author Zhiguo.Chen
 * @since 20230331
 */
@Slf4j
@Service
public class CampaignTaskTimer implements CommandLineRunner {

    public static final AtomicBoolean IS_LEADER = new AtomicBoolean(false);

    @Autowired
    private MetadataAccessService metadataAccessService;

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
            try {
                MetadataDO metadataDO = metadataAccessService.getMetadataByKey(SkyWalkerConstants.CLUSTER_LEADER);
                if (Objects.isNull(metadataDO)) {
                    metadataDO = new MetadataDO();
                    metadataDO.setMetaKey(SkyWalkerConstants.CLUSTER_LEADER);
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
                    IS_LEADER.set(false);
                    completableFuture.complete(null);
                    return completableFuture;
                }
            } catch (Exception e) {
                log.warn("RefreshClusterTask exception", e);
            }
            IS_LEADER.set(true);
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
            return "campaign-task";
        }
    }
}
