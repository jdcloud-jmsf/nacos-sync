package com.alibaba.nacossync.jmsf.configuration;

import com.alibaba.nacossync.jmsf.properties.JmsfCrdProperties;
import com.alibaba.nacossync.jmsf.v1.MeshServiceInstance;
import com.alibaba.nacossync.jmsf.v1.MeshServiceInstanceList;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(value = "jmsf.msi.enabled",  havingValue = "true", matchIfMissing = false)
@org.springframework.context.annotation.Configuration
@EnableConfigurationProperties(JmsfCrdProperties.class)
public class JmsfAutoConfiguration {

    @Bean
    public ApiClient apiClient() throws IOException {
        ApiClient apiClient = Config.defaultClient();
        apiClient.setHttpClient(
                apiClient.getHttpClient().newBuilder()
                        .readTimeout(0, TimeUnit.SECONDS).
                        connectTimeout(10, TimeUnit.SECONDS).build());
        Configuration.setDefaultApiClient(apiClient);
        return apiClient;
    }

    @Bean
    public SharedIndexInformer<MeshServiceInstance> msiInformer(
            ApiClient apiClient, SharedInformerFactory sharedInformerFactory) {
        GenericKubernetesApi<MeshServiceInstance, MeshServiceInstanceList> msiClient =
                new GenericKubernetesApi<>(MeshServiceInstance.class, MeshServiceInstanceList.class, "operator.jmsf.jd.com", "v1", "meshserviceinstances", apiClient);
        return sharedInformerFactory.sharedIndexInformerFor(msiClient, MeshServiceInstance.class, 0, "");
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }
}
