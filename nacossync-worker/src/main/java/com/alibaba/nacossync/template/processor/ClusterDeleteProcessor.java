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
package com.alibaba.nacossync.template.processor;

import com.alibaba.nacossync.dao.ClusterTaskAccessService;
import com.alibaba.nacossync.dao.TaskAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.nacossync.dao.ClusterAccessService;
import com.alibaba.nacossync.pojo.result.ClusterDeleteResult;
import com.alibaba.nacossync.pojo.request.ClusterDeleteRequest;
import com.alibaba.nacossync.template.Processor;
import org.springframework.util.CollectionUtils;

/**
 * @author NacosSync
 * @version $Id: ClusterDeleteProcessor.java, v 0.1 2018-09-30 PM2:43 NacosSync Exp $$
 */
@Slf4j
@Service
public class ClusterDeleteProcessor implements Processor<ClusterDeleteRequest, ClusterDeleteResult> {

    @Autowired
    private ClusterAccessService clusterAccessService;

    @Autowired
    private ClusterTaskAccessService clusterTaskAccessService;

    @Autowired
    private TaskAccessService taskAccessService;

    @Override
    public void process(ClusterDeleteRequest clusterDeleteRequest,
                        ClusterDeleteResult clusterDeleteResult, Object... others) throws Exception {
        if (!CollectionUtils.isEmpty(clusterTaskAccessService.findBySourceClusterId(clusterDeleteRequest.getClusterId())) ||
                !CollectionUtils.isEmpty(clusterTaskAccessService.findByDestClusterId(clusterDeleteRequest.getClusterId())) ||
                !CollectionUtils.isEmpty(taskAccessService.findBySourceClusterId(clusterDeleteRequest.getClusterId())) ||
                !CollectionUtils.isEmpty(taskAccessService.findByDestClusterId(clusterDeleteRequest.getClusterId()))) {
            log.error("该注册中心已配置同步任务，不可删除！");
            throw new RuntimeException("该注册中心已配置同步任务，不可删除！");
        } else {
            clusterAccessService.deleteByClusterId(clusterDeleteRequest.getClusterId());
        }
    }
}
