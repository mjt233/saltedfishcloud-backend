package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionInfoRepository extends JpaRepository<CollectionInfo, String> {
    List<CollectionInfo> findByUidEquals(Integer uid);
}
