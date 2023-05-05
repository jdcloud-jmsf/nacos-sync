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
package com.alibaba.nacossync.dao;

import com.alibaba.nacossync.dao.repository.ClusterTaskRepository;
import com.alibaba.nacossync.pojo.QueryCondition;
import com.alibaba.nacossync.pojo.model.ClusterTaskDO;
import com.alibaba.nacossync.pojo.model.TaskDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * ClusterTaskAccessService
 *
 * @author Zhiguo.Chen
 * @since 20230118
 */
@Service
public class ClusterTaskAccessService implements PageQueryService<ClusterTaskDO> {

    @Autowired
    private ClusterTaskRepository clusterTaskRepository;

    public ClusterTaskDO findByTaskId(String taskId) {
        return clusterTaskRepository.findByClusterTaskId(taskId);
    }

    public void deleteTaskById(String taskId) {
        clusterTaskRepository.deleteByClusterTaskId(taskId);
    }

    /**
     * batch delete tasks by taskIds
     *
     * @param taskIds
     * @author yongchao9
     */
    public void deleteTaskInBatch(List<String> taskIds) {
        List<ClusterTaskDO> tds = clusterTaskRepository.findAllByClusterTaskIdIn(taskIds);
        clusterTaskRepository.deleteInBatch(tds);
    }

    public Iterable<ClusterTaskDO> findAll() {
        return clusterTaskRepository.findAll();
    }

    public void addTask(ClusterTaskDO taskDO) {
        clusterTaskRepository.save(taskDO);
    }

    private Predicate getPredicate(CriteriaBuilder criteriaBuilder, List<Predicate> predicates) {
        Predicate[] p = new Predicate[predicates.size()];
        return criteriaBuilder.and(predicates.toArray(p));
    }

    private List<Predicate> getPredicates(Root<ClusterTaskDO> root, CriteriaBuilder criteriaBuilder, QueryCondition queryCondition) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(root.get("tenant"), queryCondition.getTenant()));
        return predicates;
    }

    @Override
    public Page<ClusterTaskDO> findPageNoCriteria(Integer pageNum, Integer size) {
        Pageable pageable = PageRequest.of(pageNum, size, Sort.Direction.DESC, "id");
        return clusterTaskRepository.findAll(pageable);
    }

    @Override
    public Page<ClusterTaskDO> findPageCriteria(Integer pageNum, Integer size, QueryCondition queryCondition) {
        Pageable pageable = PageRequest.of(pageNum, size, Sort.Direction.DESC, "id");
        return getClusterTaskDOS(queryCondition, pageable);
    }

    private Page<ClusterTaskDO> getClusterTaskDOS(QueryCondition queryCondition, Pageable pageable) {
        return clusterTaskRepository.findAll((Specification<ClusterTaskDO>) (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = getPredicates(root, criteriaBuilder, queryCondition);
            return getPredicate(criteriaBuilder, predicates);
        }, pageable);
    }

    public List<ClusterTaskDO> findBySourceClusterId(String sourceClusterId) {
        return clusterTaskRepository.findBySourceClusterId(sourceClusterId);
    }

    public List<ClusterTaskDO> findByDestClusterId(String destClusterId) {
        return clusterTaskRepository.findByDestClusterId(destClusterId);
    }


}
