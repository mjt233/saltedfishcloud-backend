package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface FileInfoRepo extends BaseRepo<FileInfo> {
    @Query("SELECT f FROM FileInfo f WHERE f.uid = :uid AND f.node = :node AND f.name = :name")
    FileInfo findFileInfo(@Param("uid") Long uid, @Param("name") String name, @Param("node") String node);

    @Query("SELECT f FROM FileInfo f WHERE f.uid = :uid AND f.node = :node AND f.name IN :names")
    List<FileInfo> findFileInfoByNames(@Param("uid") Long uid, @Param("names") Collection<String> names, @Param("node") String node);

    @Query("SELECT f FROM FileInfo f WHERE f.md5 = :md5")
    Page<FileInfo> findByMd5(@Param("md5") String md5, Pageable pageable);

    List<FileInfo> findByUidAndNode(Long uid, String node);

    @Query("DELETE FROM FileInfo WHERE uid = :uid AND node = :node AND name IN :names")
    @Modifying
    @Transactional
    int deleteFiles(@Param("uid") Long uid,@Param("node") String node,@Param("names") Collection<String> names);

    @Query("DELETE FROM FileInfo WHERE uid = :uid AND node IN :nodes")
    @Modifying
    @Transactional
    int deleteByUidAndNode(@Param("uid") Long uid, @Param("nodes") Collection<String> nodes);
}
