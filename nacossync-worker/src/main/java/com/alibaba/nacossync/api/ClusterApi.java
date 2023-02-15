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

package com.alibaba.nacossync.api;

import com.alibaba.nacossync.constant.ClusterTypeEnum;
import com.alibaba.nacossync.dao.ClusterAccessService;
import com.alibaba.nacossync.pojo.QueryCondition;
import com.alibaba.nacossync.pojo.model.ClusterDO;
import com.alibaba.nacossync.pojo.request.ClusterAddRequest;
import com.alibaba.nacossync.pojo.request.ClusterDeleteRequest;
import com.alibaba.nacossync.pojo.request.ClusterDetailQueryRequest;
import com.alibaba.nacossync.pojo.request.ClusterListQueryRequest;
import com.alibaba.nacossync.pojo.result.ClusterAddResult;
import com.alibaba.nacossync.pojo.result.ClusterDeleteResult;
import com.alibaba.nacossync.pojo.result.ClusterDetailQueryResult;
import com.alibaba.nacossync.pojo.result.ClusterListQueryResult;
import com.alibaba.nacossync.pojo.result.ClusterTypeResult;
import com.alibaba.nacossync.pojo.view.ClusterModel;
import com.alibaba.nacossync.template.SkyWalkerTemplate;
import com.alibaba.nacossync.template.processor.ClusterAddProcessor;
import com.alibaba.nacossync.template.processor.ClusterDeleteProcessor;
import com.alibaba.nacossync.template.processor.ClusterDetailQueryProcessor;
import com.alibaba.nacossync.template.processor.ClusterListQueryProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author NacosSync
 * @version $Id: ClusterApi.java, v 0.1 2018-09-25 PM9:30 NacosSync Exp $$
 */
@Slf4j
@RestController
public class ClusterApi {
    
    private final ClusterAddProcessor clusterAddProcessor;
    
    private final ClusterDeleteProcessor clusterDeleteProcessor;
    
    private final ClusterDetailQueryProcessor clusterDetailQueryProcessor;
    
    private final ClusterListQueryProcessor clusterListQueryProcessor;

    private final ClusterAccessService clusterAccessService;
    
    public ClusterApi(ClusterAddProcessor clusterAddProcessor, ClusterDeleteProcessor clusterDeleteProcessor,
            ClusterDetailQueryProcessor clusterDetailQueryProcessor,
            ClusterListQueryProcessor clusterListQueryProcessor, ClusterAccessService clusterAccessService) {
        this.clusterAddProcessor = clusterAddProcessor;
        this.clusterDeleteProcessor = clusterDeleteProcessor;
        this.clusterDetailQueryProcessor = clusterDetailQueryProcessor;
        this.clusterListQueryProcessor = clusterListQueryProcessor;
        this.clusterAccessService = clusterAccessService;
    }
    
    @RequestMapping(path = "/v1/cluster/list", method = RequestMethod.GET)
    public ClusterListQueryResult clusters(ClusterListQueryRequest clusterListQueryRequest) {
        
        return SkyWalkerTemplate.run(clusterListQueryProcessor, clusterListQueryRequest, new ClusterListQueryResult());
    }

    @RequestMapping(path = "/v1/cluster/listByMeshId", method = RequestMethod.GET)
    public ClusterListQueryResult clusters(@RequestParam(name = "meshId") String meshId) {
        ClusterListQueryResult result = new ClusterListQueryResult();
        if (StringUtils.isEmpty(meshId)) {
            result.setSuccess(false);
            return result;
        }
        List<ClusterDO> clusterDOS = clusterAccessService.findByMeshId(meshId);
        List<ClusterModel> clusterModels = new ArrayList<>();
        clusterDOS.forEach(clusterDO -> {
            ClusterModel clusterModel = new ClusterModel();
            clusterModel.setClusterId(clusterDO.getClusterId());
            clusterModel.setClusterName(clusterDO.getClusterName());
            clusterModel.setClusterType(clusterDO.getClusterType());
            clusterModel.setConnectKeyList(clusterDO.getConnectKeyList());
            clusterModel.setNamespace(clusterDO.getNamespace());
            clusterModel.setMeshId(clusterDO.getMeshId());
            clusterModels.add(clusterModel);
        });
        result.setClusterModels(clusterModels);
        result.setCurrentSize(clusterModels.size());
        return result;
    }
    
    @RequestMapping(path = "/v1/cluster/detail", method = RequestMethod.GET)
    public ClusterDetailQueryResult getByClusterId(ClusterDetailQueryRequest clusterDetailQueryRequest) {
        
        return SkyWalkerTemplate.run(clusterDetailQueryProcessor, clusterDetailQueryRequest,
                new ClusterDetailQueryResult());
    }
    
    @RequestMapping(path = "/v1/cluster/delete", method = RequestMethod.DELETE)
    public ClusterDeleteResult deleteCluster(ClusterDeleteRequest clusterDeleteRequest) {
        
        return SkyWalkerTemplate.run(clusterDeleteProcessor, clusterDeleteRequest, new ClusterDeleteResult());
        
    }
    
    @RequestMapping(path = "/v1/cluster/add", method = RequestMethod.POST)
    public ClusterAddResult clusterAdd(@RequestBody ClusterAddRequest clusterAddRequest) {
        
        return SkyWalkerTemplate.run(clusterAddProcessor, clusterAddRequest, new ClusterAddResult());
    }
    
    @RequestMapping(path = "/v1/cluster/types", method = RequestMethod.GET)
    public ClusterTypeResult getClusterType() {
        
        return new ClusterTypeResult(ClusterTypeEnum.getClusterTypeCodes());
    }
    
}
