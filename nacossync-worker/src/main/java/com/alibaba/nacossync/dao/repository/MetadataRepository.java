package com.alibaba.nacossync.dao.repository;

import com.alibaba.nacossync.pojo.model.MetadataDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import javax.transaction.Transactional;

/**
 * MetadataRepository
 *
 * @author Zhiguo.Chen
 * @since 20230118
 */
public interface MetadataRepository extends CrudRepository<MetadataDO, String>, JpaRepository<MetadataDO, String>,
        JpaSpecificationExecutor<MetadataDO> {

    MetadataDO findByMetaKey(String key);

    // @Modifying
    // @Query("update MetadataDO m set m.value=?1 where  m.version=?2")
    // void updateByVersion(String value, String version);

    @Transactional
    int deleteByMetaKey(String key);
}
