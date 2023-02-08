package com.alibaba.nacossync.dao;

import com.alibaba.nacossync.dao.repository.MetadataRepository;
import com.alibaba.nacossync.pojo.model.MetadataDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MetadataAccessService
 *
 * @author Zhiguo.Chen
 * @since 20230118
 */
@Service
public class MetadataAccessService {

    private final MetadataRepository metadataRepository;

    @Autowired
    public MetadataAccessService(MetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    public MetadataDO getMetadataByKey(String key) {
        return metadataRepository.findByMetaKey(key);
    }

    public MetadataDO saveMetadataByKey(MetadataDO metadataDO) {
        return metadataRepository.save(metadataDO);
    }
}
