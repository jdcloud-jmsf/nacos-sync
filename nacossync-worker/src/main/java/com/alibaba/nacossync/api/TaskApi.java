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

package com.alibaba.nacossync.api;

import com.alibaba.nacossync.cache.SkyWalkerCacheServices;
import com.alibaba.nacossync.constant.TaskStatusEnum;
import com.alibaba.nacossync.dao.ClusterAccessService;
import com.alibaba.nacossync.dao.ClusterTaskAccessService;
import com.alibaba.nacossync.dao.TaskAccessService;
import com.alibaba.nacossync.exception.SkyWalkerException;
import com.alibaba.nacossync.pojo.QueryCondition;
import com.alibaba.nacossync.pojo.model.ClusterDO;
import com.alibaba.nacossync.pojo.model.ClusterTaskDO;
import com.alibaba.nacossync.pojo.model.TaskDO;
import com.alibaba.nacossync.pojo.request.*;
import com.alibaba.nacossync.pojo.result.*;
import com.alibaba.nacossync.pojo.view.ClusterTaskModel;
import com.alibaba.nacossync.pojo.view.TaskModel;
import com.alibaba.nacossync.template.SkyWalkerTemplate;
import com.alibaba.nacossync.template.processor.TaskAddAllProcessor;
import com.alibaba.nacossync.template.processor.TaskAddProcessor;
import com.alibaba.nacossync.template.processor.TaskDeleteInBatchProcessor;
import com.alibaba.nacossync.template.processor.TaskDeleteProcessor;
import com.alibaba.nacossync.template.processor.TaskDetailProcessor;
import com.alibaba.nacossync.template.processor.TaskListQueryProcessor;
import com.alibaba.nacossync.template.processor.TaskUpdateProcessor;
import com.alibaba.nacossync.util.SkyWalkerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author NacosSync
 * @version $Id: Task.java, v 0.1 2018-09-24 PM3:43 NacosSync Exp $$
 */
@Slf4j
@RestController
public class TaskApi {

    private final TaskUpdateProcessor taskUpdateProcessor;

    private final TaskAddProcessor taskAddProcessor;

    private final TaskAddAllProcessor taskAddAllProcessor;

    private final TaskDeleteProcessor taskDeleteProcessor;

    private final TaskDeleteInBatchProcessor taskDeleteInBatchProcessor;

    private final TaskListQueryProcessor taskListQueryProcessor;

    private final TaskDetailProcessor taskDetailProcessor;

    private final TaskAccessService taskAccessService;

    private final ClusterTaskAccessService clusterTaskAccessService;

    private final ClusterAccessService clusterAccessService;

    private final SkyWalkerCacheServices skyWalkerCacheServices;

    public TaskApi(TaskUpdateProcessor taskUpdateProcessor, TaskAddProcessor taskAddProcessor,
                   TaskAddAllProcessor taskAddAllProcessor, TaskDeleteProcessor taskDeleteProcessor,
                   TaskDeleteInBatchProcessor taskDeleteInBatchProcessor, TaskListQueryProcessor taskListQueryProcessor,
                   TaskDetailProcessor taskDetailProcessor, TaskAccessService taskAccessService,
                   ClusterTaskAccessService clusterTaskAccessService, ClusterAccessService clusterAccessService,
                   SkyWalkerCacheServices skyWalkerCacheServices) {
        this.taskUpdateProcessor = taskUpdateProcessor;
        this.taskAddProcessor = taskAddProcessor;
        this.taskAddAllProcessor = taskAddAllProcessor;
        this.taskDeleteProcessor = taskDeleteProcessor;
        this.taskDeleteInBatchProcessor = taskDeleteInBatchProcessor;
        this.taskListQueryProcessor = taskListQueryProcessor;
        this.taskDetailProcessor = taskDetailProcessor;
        this.taskAccessService = taskAccessService;
        this.clusterTaskAccessService = clusterTaskAccessService;
        this.clusterAccessService = clusterAccessService;
        this.skyWalkerCacheServices = skyWalkerCacheServices;
    }

    @RequestMapping(path = "/v1/task/list", method = RequestMethod.GET)
    public TaskListQueryResult tasks(TaskListQueryRequest taskListQueryRequest) {

        return SkyWalkerTemplate.run(taskListQueryProcessor, taskListQueryRequest, new TaskListQueryResult());
    }

    @RequestMapping(path = "/v1/task/detail", method = RequestMethod.GET)
    public TaskDetailQueryResult getByTaskId(TaskDetailQueryRequest taskDetailQueryRequest) {

        return SkyWalkerTemplate.run(taskDetailProcessor, taskDetailQueryRequest, new TaskDetailQueryResult());
    }

    @RequestMapping(path = "/v1/task/delete", method = RequestMethod.DELETE)
    public BaseResult deleteTask(TaskDeleteRequest taskDeleteRequest) {

        return SkyWalkerTemplate.run(taskDeleteProcessor, taskDeleteRequest, new BaseResult());
    }

    /**
     * @param taskBatchDeleteRequest
     * @return
     * @author yongchao9
     */
    @RequestMapping(path = "/v1/task/deleteInBatch", method = RequestMethod.DELETE)
    public BaseResult batchDeleteTask(TaskDeleteInBatchRequest taskBatchDeleteRequest) {
        return SkyWalkerTemplate.run(taskDeleteInBatchProcessor, taskBatchDeleteRequest, new BaseResult());
    }

    @RequestMapping(path = "/v1/task/add", method = RequestMethod.POST)
    public BaseResult taskAdd(@RequestBody TaskAddRequest addTaskRequest) {

        return SkyWalkerTemplate.run(taskAddProcessor, addTaskRequest, new TaskAddResult());
    }

    /**
     * <p>
     * 支持从 sourceCluster 获取所有 service，然后生成同步到 destCluster 的任务。
     * </p>
     */
    @RequestMapping(path = "/v1/task/addAll", method = RequestMethod.POST)
    public BaseResult taskAddAll(@RequestBody TaskAddAllRequest addAllRequest) {
        return SkyWalkerTemplate.run(taskAddAllProcessor, addAllRequest, new TaskAddResult());
    }

    @RequestMapping(path = "/v1/task/addClusterTask", method = RequestMethod.PUT)
    public BaseResult addClusterTask(String meshIds) {
        String[] meshIdList = meshIds.split(",");
        for (String meshId : meshIdList) {
            List<ClusterDO> clusterDOS = clusterAccessService.findByMeshId(meshId);
            if (CollectionUtils.isEmpty(clusterDOS)) {
                continue;
            }
            for (ClusterDO clusterDO : clusterDOS) {
                TaskAddAllRequest addAllRequest = new TaskAddAllRequest();
                addAllRequest.setSourceClusterId(clusterDO.getClusterId());
                for (String id : meshIdList) {
                    if (id.equalsIgnoreCase(meshId)) {
                        continue;
                    }
                    List<ClusterDO> clusters = clusterAccessService.findByMeshId(id);
                    if (CollectionUtils.isEmpty(clusterDOS)) {
                        continue;
                    }
                    for (ClusterDO cluster : clusters) {
                        addAllRequest.setDestClusterId(cluster.getClusterId());
                        SkyWalkerTemplate.run(taskAddAllProcessor, addAllRequest, new TaskAddResult());
                    }
                }
            }

        }
        return new TaskAddResult();
    }

    @RequestMapping(path = "/v1/task/deleteClusterTask", method = RequestMethod.DELETE)
    public BaseResult deleteClusterTask(TaskDeleteRequest taskDeleteRequest) {
        BaseResult result = new BaseResult();
        try {
            ClusterTaskDO clusterTaskDO = clusterTaskAccessService.findByTaskId(taskDeleteRequest.getTaskId());
            if (Objects.isNull(clusterTaskDO)) {
                result.setResultMessage("未查询到该任务！");
                result.setSuccess(false);
            } else {
                if (!TaskStatusEnum.DELETE.getCode().equals(clusterTaskDO.getTaskStatus())) {
                    result.setResultMessage("该任务未暂停，请暂停后再执行删除操作！");
                    result.setSuccess(false);
                } else {
                    List<TaskDO> taskDOList = taskAccessService.findByClusterTaskId(clusterTaskDO.getClusterTaskId());
                    for (TaskDO taskDO : taskDOList) {
                        taskAccessService.deleteTaskById(taskDO.getTaskId());
                    }
                    clusterTaskAccessService.deleteTaskById(clusterTaskDO.getClusterTaskId());
                }
            }
        } catch (Throwable e) {
            log.error("processor.process error", e);
            SkyWalkerTemplate.initExceptionResult(result, e);
        }
        return result;
    }

    @RequestMapping(path = "/v1/task/clusterTaskList", method = RequestMethod.GET)
    public ClusterTaskListQueryResult clusterTasks(TaskListQueryRequest taskListQueryRequest) {
        Page<ClusterTaskDO> clusterTaskDOS;
        ClusterTaskListQueryResult taskListQueryResult = new ClusterTaskListQueryResult();
        try {
            QueryCondition queryCondition = new QueryCondition();
            queryCondition.setTenant(taskListQueryRequest.getTenant());
            clusterTaskDOS = clusterTaskAccessService.findPageCriteria(taskListQueryRequest.getPageNum() - 1,
                    taskListQueryRequest.getPageSize(), queryCondition);
            List<ClusterTaskModel> taskList = new ArrayList<>();
            clusterTaskDOS.forEach(taskDO -> {
                ClusterTaskModel taskModel = new ClusterTaskModel();
                taskModel.setClusterTaskId(taskDO.getClusterTaskId());
                taskModel.setDestClusterId(taskDO.getDestClusterId());
                taskModel.setSourceClusterId(taskDO.getSourceClusterId());
                taskModel.setGroupName(taskDO.getGroupName());
                taskModel.setTaskStatus(taskDO.getTaskStatus());
                taskList.add(taskModel);
            });
            taskListQueryResult.setTaskModels(taskList);
            taskListQueryResult.setTotalPage(clusterTaskDOS.getTotalPages());
            taskListQueryResult.setTotalSize(clusterTaskDOS.getTotalElements());
            taskListQueryResult.setCurrentSize(taskList.size());
        } catch (Exception e) {
            log.error("processor.process error", e);
            SkyWalkerTemplate.initExceptionResult(taskListQueryResult, e);
        }
        return taskListQueryResult;
    }

    @RequestMapping(path = "/v1/task/clusterTaskDetail", method = RequestMethod.GET)
    public ClusterTaskDetailQueryResult getByClusterTaskId(ClusterTaskDetailQueryRequest request) {
        ClusterTaskDetailQueryResult result = new ClusterTaskDetailQueryResult();
        try {
            ClusterTaskDO clusterTaskDO = clusterTaskAccessService.findByTaskId(request.getClusterTaskId());
            if (null == clusterTaskDO) {
                throw new SkyWalkerException("clusterTaskDO is null, clusterTaskId :" + request.getClusterTaskId());
            }
            ClusterTaskModel taskModel = new ClusterTaskModel();
            taskModel.setDestClusterId(clusterTaskDO.getDestClusterId());
            taskModel.setGroupName(clusterTaskDO.getGroupName());
            taskModel.setSourceClusterId(clusterTaskDO.getSourceClusterId());
            taskModel.setTaskStatus(clusterTaskDO.getTaskStatus());
            taskModel.setClusterTaskId(clusterTaskDO.getClusterTaskId());
            result.setClusterTaskModel(taskModel);
        } catch (Exception e) {
            log.error("processor.process error", e);
            SkyWalkerTemplate.initExceptionResult(result, e);
        }
        return result;
    }

    @RequestMapping(path = "/v1/task/update", method = RequestMethod.POST)
    public BaseResult updateTask(@RequestBody TaskUpdateRequest taskUpdateRequest) {

        return SkyWalkerTemplate.run(taskUpdateProcessor, taskUpdateRequest, new BaseResult());
    }

    @RequestMapping(path = "/v1/task/updateClusterTask", method = RequestMethod.POST)
    public BaseResult updateClusterTask(@RequestBody TaskUpdateRequest taskUpdateRequest) {
        BaseResult result = new BaseResult();
        try {
            ClusterTaskDO clusterTaskDO = clusterTaskAccessService.findByTaskId(taskUpdateRequest.getClusterTaskId());
            if (Objects.isNull(clusterTaskDO)) {
                result.setResultMessage("未查询到该任务！");
                result.setSuccess(false);
            } else {
                clusterTaskDO.setTaskStatus(TaskStatusEnum.valueOf(taskUpdateRequest.getTaskStatus()).getCode());
                clusterTaskAccessService.addTask(clusterTaskDO);
                List<TaskDO> taskDOList = taskAccessService.findByClusterTaskId(clusterTaskDO.getClusterTaskId());
                for (TaskDO taskDO : taskDOList) {
                    String operationId = taskDO.getOperationId();
                    taskDO.setOperationId(SkyWalkerUtil.generateOperationId());
                    taskDO.setTaskStatus(TaskStatusEnum.valueOf(taskUpdateRequest.getTaskStatus()).getCode());
                    taskAccessService.addTask(taskDO);
                    skyWalkerCacheServices.removeFinishedTask(operationId);
                }
            }
        } catch (Throwable e) {
            log.error("processor.process error", e);
            SkyWalkerTemplate.initExceptionResult(result, e);
        }
        return result;
    }
}
