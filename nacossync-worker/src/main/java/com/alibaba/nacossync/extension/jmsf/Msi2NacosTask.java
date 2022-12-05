package com.alibaba.nacossync.extension.jmsf;


import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacossync.extension.holder.NacosServerHolder;
import com.alibaba.nacossync.jmsf.constant.JmsfConstants;
import com.alibaba.nacossync.jmsf.v1.MeshServiceInstance;
import com.google.gson.JsonObject;
import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.extended.leaderelection.resourcelock.EndpointsLock;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.alibaba.nacossync.constant.SkyWalkerConstants.PAGE_NO;
import static com.alibaba.nacossync.constant.SkyWalkerConstants.PAGE_SIZE;

@Slf4j
@Configuration
public class Msi2NacosTask {

    private final AtomicBoolean isLeader = new AtomicBoolean(true);
    private LeaderElector leaderElector;

    @Autowired
    private SharedInformerFactory informerFactory;

    @Autowired
    private SharedIndexInformer<MeshServiceInstance> msiInformer;

    @Autowired
    private  NacosServerHolder nacosServerHolder;

    @Autowired

    @PostConstruct
    private void onStarted() throws InterruptedException {
        leaderElection();
        startInformerAndWaitForSync();
        addInformerHandler();
    }

    /**
     * leader election in order to create cm in mesh cluster.
     */
    private void leaderElection() {
        String appNamespace = "mesh";
        String appName = "leader-election-nacos-sync";
        String lockHolderIdentityName = UUID.randomUUID().toString(); // Anything unique
        EndpointsLock lock = new EndpointsLock(appNamespace, appName, lockHolderIdentityName);
        LeaderElectionConfig leaderElectionConfig =
                new LeaderElectionConfig(
                        lock, Duration.ofMillis(10000), Duration.ofMillis(8000), Duration.ofMillis(5000));
        leaderElector = new LeaderElector(leaderElectionConfig);
        Thread thread = new Thread(() -> leaderElector.run(
                () -> {
                    log.info("getting leadership.");
                    isLeader.set(true);
                },
                () -> {
                    log.info("losing leadership.");
                    isLeader.set(false);
                }));
        thread.setDaemon(true);
        thread.setName("thread-leadership");
        thread.start();
    }

    /**
     *  start informer and wait for syncer
     */
    private void startInformerAndWaitForSync() throws InterruptedException {
        informerFactory.startAllRegisteredInformers();
        boolean hasSynced = false;
        while (!hasSynced) {
            Thread.sleep(1000);
            if (msiInformer.hasSynced()) {
                hasSynced = true;
            }
        }
    }

    /**
     * add event handler
     */
    private void addInformerHandler() {
        // TODO resync msi
//        msiInformer.getIndexer().list()
        msiInformer.addEventHandler(
                new ResourceEventHandler<MeshServiceInstance>() {
                    @Override
                    public void onAdd(MeshServiceInstance msi) {
                        log.debug("receive secret add event" + Objects.requireNonNull(msi.getMetadata()).getName());
                    }

                    @Override
                    public void onUpdate(MeshServiceInstance target, MeshServiceInstance origin) {
                        log.info("receive secret update event" + target.toString());
                        doSync(target);
                    }

                    @Override
                    public void onDelete(MeshServiceInstance msi, boolean b) {
                        log.debug("receive secret delete event" + Objects.requireNonNull(msi.getMetadata()).getName());
                    }
                }

        );
    }

    private void doSync(MeshServiceInstance msi) {
        if (!System.getenv().containsKey("MSI_ENABLED")) {
            return;
        }
        if (!isLeader.get()) {
            return;
        }
        if (msi.getMetadata().getAnnotations() == null) {
            log.info("无效的msi: {}，注解为null", msi.getMetadata().getName());
            return;
        }

        if (!msi.getMetadata().getAnnotations().containsKey(JmsfConstants.SYNC_TASK_ID_ANNOTATION)) {
            log.info("无效的msi: {}，注解不存在: {}", msi.getMetadata().getName(), JmsfConstants.SYNC_TASK_ID_ANNOTATION);
            return;
        }
        String syncTaskId = msi.getMetadata().getAnnotations().get(JmsfConstants.SYNC_TASK_ID_ANNOTATION);
        if (StringUtils.isEmpty(syncTaskId)) {
            log.info("无效的msi: {}，注解值为空: {}", msi.getMetadata().getName(), JmsfConstants.SYNC_TASK_ID_ANNOTATION);
            return;
        }

        if (!msi.getMetadata().getAnnotations().containsKey(JmsfConstants.DEST_CLUSTER_ANNOTATION)) {
            log.info("无效的msi: {}，注解不存在: {}", msi.getMetadata().getName(), JmsfConstants.DEST_CLUSTER_ANNOTATION);
            return;
        }
        String destClusterId =  msi.getMetadata().getAnnotations().get(JmsfConstants.DEST_CLUSTER_ANNOTATION);
        if (StringUtils.isEmpty(destClusterId)) {
            log.info("无效的msi: {}，注解值为空: {}", msi.getMetadata().getName(), JmsfConstants.DEST_CLUSTER_ANNOTATION);
            return;
        }

        if (!msi.getMetadata().getAnnotations().containsKey(JmsfConstants.SOURCE_CLUSTER_ANNOTATION)) {
            log.info("无效的msi: {}，注解不存在: {}", msi.getMetadata().getName(), JmsfConstants.SOURCE_CLUSTER_ANNOTATION);
            return;
        }
        String sourceClusterId = msi.getMetadata().getAnnotations().get(JmsfConstants.SOURCE_CLUSTER_ANNOTATION);
        if (StringUtils.isEmpty(sourceClusterId)) {
            log.info("无效的msi: {}，注解值为空: {}", msi.getMetadata().getName(), JmsfConstants.SOURCE_CLUSTER_ANNOTATION);
            return;
        }

        if (!msi.getMetadata().getAnnotations().containsKey(JmsfConstants.SYNC_SOURCE_ANNOTATION)) {
            log.info("无效的msi: {}，注解不存在: {}", msi.getMetadata().getName(), JmsfConstants.SYNC_SOURCE_ANNOTATION);
            return;
        }
        String syncSource = msi.getMetadata().getAnnotations().get(JmsfConstants.SYNC_SOURCE_ANNOTATION);
        if (StringUtils.isEmpty(syncSource)) {
            log.info("无效的msi: {}，实例ip为空", msi.getMetadata().getName());
            return;
        }
        if (!JmsfConstants.NACOS_SYNC_SOURCE.equals(syncSource)) {
            log.info("目前注册中心为: {}，非Nacos注册中心暂不支持同步实例状态。", syncSource);
            return;
        }
        if (msi.getStatus() == null) {
            log.info("无效的msi: {}，状态信息不存在。", msi.getMetadata().getName());
            return;
        }
        boolean msiIsEnabled = false;
        if (JmsfConstants.HANG_UP_SYNC_STATUS.equalsIgnoreCase(msi.getStatus().getPhase())) {
            msiIsEnabled = false;
        }
        else if (JmsfConstants.ONLINE_STATUS.equalsIgnoreCase(msi.getStatus().getPhase())) {
            msiIsEnabled = true;
        } else {
            log.info("无效的msi: {}，状态信息未知，不需要处理。", msi.getStatus().getPhase());
            return;
        }

        String ip = msi.getSpec().getIp();
        if (StringUtils.isEmpty(ip)) {
            log.info("无效的msi: {}，实例ip为空", msi.getMetadata().getName());
            return;
        }

        String serviceName = msi.getSpec().getServiceName();
        if (StringUtils.isEmpty(serviceName)) {
            log.info("无效的msi: {}，服务名为空", msi.getMetadata().getName());
            return;
        }

        NamingService sourceNamingService = nacosServerHolder.get(sourceClusterId);
        if (sourceNamingService == null) {
            log.error("only support sync type that the source of the Nacos.");
            return;
        }
        boolean needSwitch = false;
        Instance ni = null;
        final Msi2NacosTask.EnhanceNamingService enhanceNamingService = new Msi2NacosTask.EnhanceNamingService(sourceNamingService);
        // TODO group?
        try {
            CatalogInstanceResult result = enhanceNamingService.catalogInstances(serviceName, "DEFAULT_GROUP");
            if (result == null) {
                log.info("实例不需要同步，注册中心: {}，未查到服务实例: {}", sourceClusterId, serviceName);
                return;
            }
            if (result.count == 0) {
                return;
            }
            Optional<Instance> optional = result.list.stream().filter(i -> i.getIp().equals(ip)).findFirst();
            if (!optional.isPresent()) {
                log.info("服务: {}, 注册中心: {}, 未找到实例: {}", msi.getMetadata().getName(), sourceClusterId, ip);
                return;
            }
            ni = optional.get();
            if (ni.isEnabled() != msiIsEnabled) {
                needSwitch = true;
                ni.setEnabled(msiIsEnabled);
            }
        } catch (NacosException e) {
            log.info("服务: {}, 注册中心: {}, 实例查询失败: {}。", msi.getMetadata().getName(), sourceClusterId, ExceptionUtil.getStackTrace(e));
            return;
        }
        if (needSwitch) {
            try {
                String result = enhanceNamingService.switchInstanceState(serviceName,"DEFAULT_GROUP", ni);
                log.info("服务: {}, 注册中心: {}, 修改实例状态: {} --> {}, {}！", msi.getMetadata().getName(), sourceClusterId, !msiIsEnabled, msiIsEnabled, result);
            } catch (NacosException e) {
                log.error("服务: {}, 注册中心: {}, 修改实例状态: {} --> {}, 失败: {}。", msi.getMetadata().getName(), sourceClusterId, !msiIsEnabled, msiIsEnabled, ExceptionUtil.getStackTrace(e));
            }
        }
    }

    @PreDestroy
    private void onStopped() {
        // 1.stop informer.
        informerFactory.stopAllRegisteredInformers();
        // 2.close leader election.
        leaderElector.close();
    }

    static class EnhanceNamingService {

        protected NamingService delegate;

        protected NamingProxy serverProxy;

        protected EnhanceNamingService(NamingService namingService) {
            if (!(namingService instanceof NacosNamingService)) {
                throw new IllegalArgumentException(
                        "namingService only support instance of com.alibaba.nacos.client.naming.NacosNamingService.");
            }
            this.delegate = namingService;

            // serverProxy
            final Field serverProxyField = ReflectionUtils.findField(NacosNamingService.class, "serverProxy");
            assert serverProxyField != null;
            ReflectionUtils.makeAccessible(serverProxyField);
            this.serverProxy = (NamingProxy) ReflectionUtils.getField(serverProxyField, delegate);
        }

        /**
         * 查询实例列表
         */
        public Msi2NacosTask.CatalogInstanceResult catalogInstances(@Nullable String serviceName, @Nullable String group) throws NacosException {
            final Map<String, String> params = new HashMap<>(8);
            params.put(CommonParams.NAMESPACE_ID, serverProxy.getNamespaceId());
            params.put(CommonParams.SERVICE_NAME, serviceName);
            params.put(CommonParams.CLUSTER_NAME, "DEFAULT");
            params.put(CommonParams.GROUP_NAME, group);
            params.put(PAGE_NO, String.valueOf(1));
            params.put(PAGE_SIZE, String.valueOf(100));

            final String result = this.serverProxy.reqApi(UtilAndComs.nacosUrlBase + "/catalog/instances", params,
                    HttpMethod.GET);
            log.info("注册中心实例查询结果:{}", result);
            if (StringUtils.isNotEmpty(result)) {
                return JacksonUtils.toObj(result, Msi2NacosTask.CatalogInstanceResult.class);
            }
            return null;
        }

        /**
         * 更新实例状态
         */
        public String switchInstanceState(@Nullable String serviceName, @Nullable String group, Instance instance) throws NacosException {
            Map<String, String> params = new HashMap<>(8);
            params.put(CommonParams.NAMESPACE_ID, serverProxy.getNamespaceId());
            params.put(CommonParams.SERVICE_NAME, serviceName);
            params.put(CommonParams.CLUSTER_NAME, "DEFAULT");
            params.put(CommonParams.GROUP_NAME, group);


            Map<String, String> body =  new HashMap<>();
            params.put(CommonParams.SERVICE_NAME, serviceName);
            params.put(CommonParams.CLUSTER_NAME, "DEFAULT");
            params.put(CommonParams.GROUP_NAME, group);
            params.put(CommonParams.NAMESPACE_ID, serverProxy.getNamespaceId());
            params.put("ip", instance.getIp());
            params.put("port", String.valueOf(instance.getPort()));
            params.put("ephemeral", String.valueOf(instance.isEphemeral()));
            params.put("weight", String.valueOf(instance.getWeight()));
            params.put("enabled", String.valueOf(instance.isEnabled()));
            body.put("metadata", JSONObject.toJSONString(instance.getMetadata()));
            final String result = this.serverProxy.reqApi(UtilAndComs.nacosUrlInstance, params, body,
                    HttpMethod.PUT);
            if (StringUtils.isNotEmpty(result)) {
                return result;
            }
            return null;
        }

    }

    @Data
    static class CatalogInstanceResult {

        /**
         * count，not equal serviceList.size .
         */
        private int count;

        private List<Instance> list;

    }

}
