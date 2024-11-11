package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface NodeInfoRepo extends JpaRepository<NodeInfo, String> {

    @Transactional
    @Modifying
    @Query("DELETE FROM NodeInfo WHERE id IN :ids AND uid = :uid")
    int deleteByIdAndUid(@Param("uid") Long uid, @Param("ids") Collection<String> ids);


    @Query("SELECT n FROM NodeInfo n WHERE n.uid = :uid AND n.parent IN :parent")
    List<NodeInfo> findByUidAndParent(@Param("uid") Long uid, @Param("parent") Collection<String> parent);
}
