package com.alibaba.nacossync.jmsf.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(JmsfCrdProperties.PREFIX)
public class JmsfCrdProperties {

    public static final String PREFIX = "jmsf.msi";


    private boolean enabled = true;


}
