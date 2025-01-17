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
package com.alibaba.nacossync.pojo.request;

import com.alibaba.nacossync.constant.ClusterTypeEnum;
import java.util.List;

import lombok.Data;

/**
 * @author NacosSync
 * @version $Id: AddClusterRequest.java, v 0.1 2018-09-25 PM 10:27 NacosSync Exp $$
 */
@Data
public class ClusterAddRequest extends BaseRequest {

    /**
     * json format,eg ["192.168.1:8080","192.168.2?key=1"]
     */
    private List<String> connectKeyList;
    /**
     * cluster name, Used to display, eg：cluster of ShangHai（edas-sh）
     */
    private String clusterName;
    /**
     * cluster type，eg cluster of CS(CS)，cluster of Nacos(NACOS)
     * @see ClusterTypeEnum
     */
    private String clusterType;

    /**
     * The username of the Nacos.
     *
     */
    private String userName;

    /**
     * The password of the Nacos.
     */
    private String password;
    private String namespace;
    private String meshId;
}
