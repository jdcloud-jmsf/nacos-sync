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
package com.alibaba.nacossync.extension.holder;

import com.alibaba.nacossync.dao.ClusterAccessService;
import com.alibaba.nacossync.pojo.model.ClusterDO;
import com.ecwid.consul.v1.ConsulClient;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URL;

/**
 * @author paderlol
 * @date: 2018-12-31 16:26
 */
@Service
@Slf4j
public class ConsulServerHolder extends AbstractServerHolderImpl<ConsulClient> {

    public static final String HTTP = "http://";

    private final ClusterAccessService clusterAccessService;

    private static final Map<String, String> tokenMap = new ConcurrentHashMap<>();

    public ConsulServerHolder(ClusterAccessService clusterAccessService) {
        this.clusterAccessService = clusterAccessService;
    }

    @Override
    ConsulClient createServer(String clusterId, Supplier<String> serverAddressSupplier) throws Exception {
        ClusterDO clusterDO = clusterAccessService.findByClusterId(clusterId);
        if (Objects.nonNull(clusterDO) && StringUtils.hasText(clusterDO.getPassword())) {
            tokenMap.put(clusterId, clusterDO.getPassword());
        }
        String serverAddress = serverAddressSupplier.get();
        serverAddress = serverAddress.startsWith(HTTP) ? serverAddress : HTTP + serverAddress;
        URL url = new URL(serverAddress);
        return new ConsulClient(url.getHost(), url.getPort());
    }

    public static String getToken(String clusterId) {
        return tokenMap.get(clusterId);
    }

}
