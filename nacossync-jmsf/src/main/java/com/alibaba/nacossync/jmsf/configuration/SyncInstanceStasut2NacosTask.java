package com.alibaba.nacossync.jmsf.configuration;

import com.alibaba.nacossync.jmsf.v1.MeshServiceInstance;
import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.extended.leaderelection.resourcelock.EndpointsLock;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SyncInstanceStasut2NacosTask {

    private final AtomicBoolean isLeader = new AtomicBoolean(true);
    private LeaderElector leaderElector;

    @Autowired
    private SharedInformerFactory informerFactory;

    @Autowired
    private SharedIndexInformer<MeshServiceInstance> msiInformer;

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
        msiInformer.addEventHandler(
                new ResourceEventHandler<MeshServiceInstance>() {
                    @Override
                    public void onAdd(MeshServiceInstance msi) {
                        log.debug("receive secret add event" + Objects.requireNonNull(msi.getMetadata()).getName());
                    }

                    @Override
                    public void onUpdate(MeshServiceInstance target, MeshServiceInstance origin) {
                        log.debug("receive secret update event" + Objects.requireNonNull(target.getMetadata()).getName());
                    }

                    @Override
                    public void onDelete(MeshServiceInstance msi, boolean b) {
                        log.debug("receive secret delete event" + Objects.requireNonNull(msi.getMetadata()).getName());
                    }
                }

        );
    }

    @PreDestroy
    private void onStopped() {
        // 1.stop informer.
        informerFactory.stopAllRegisteredInformers();
        // 2.close leader election.
        leaderElector.close();
    }

}
