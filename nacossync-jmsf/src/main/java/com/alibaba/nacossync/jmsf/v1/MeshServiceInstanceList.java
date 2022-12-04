package com.alibaba.nacossync.jmsf.v1;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
@ToString
public class MeshServiceInstanceList implements KubernetesListObject {
    public static final String SERIALIZED_NAME_API_VERSION = "apiVersion";
    @SerializedName("apiVersion")
    private String apiVersion;
    public static final String SERIALIZED_NAME_ITEMS = "items";
    @SerializedName("items")
    private List<MeshServiceInstance> items = new ArrayList();
    public static final String SERIALIZED_NAME_KIND = "kind";
    @SerializedName("kind")
    private String kind;
    public static final String SERIALIZED_NAME_METADATA = "metadata";
    @SerializedName("metadata")
    private V1ListMeta metadata;

    public MeshServiceInstanceList() {
    }
}
