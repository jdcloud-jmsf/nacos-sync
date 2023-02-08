package com.alibaba.nacossync.pojo.request;

import lombok.Data;

/**
 * ClusterTaskAddRequest
 *
 * @author Zhiguo.Chen
 * @since 20230118
 */
@Data
public class ClusterTaskAddRequest extends BaseRequest {
    private String sourceClusterId;
    private String destClusterId;
    private String version;
    private String nameSpace;
}
