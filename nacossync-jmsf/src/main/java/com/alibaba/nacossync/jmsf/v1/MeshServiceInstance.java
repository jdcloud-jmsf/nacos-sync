package com.alibaba.nacossync.jmsf.v1;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@ToString
public class MeshServiceInstance implements KubernetesObject {
    public static final String SERIALIZED_NAME_API_VERSION = "apiVersion";
    @SerializedName("apiVersion")
    private String apiVersion;
    public static final String SERIALIZED_NAME_KIND = "kind";
    @SerializedName("kind")
    private String kind;
    public static final String SERIALIZED_NAME_METADATA = "metadata";
    @SerializedName("metadata")
    private V1ObjectMeta metadata;
    public static final String SERIALIZED_NAME_SPEC = "spec";
    @SerializedName("spec")
    private MeshServiceInstanceSpec spec;
    public static final String SERIALIZED_NAME_STATUS = "status";
    @SerializedName("status")
    private MeshServiceInstanceStatus status;

    public MeshServiceInstance() {
    }
}
