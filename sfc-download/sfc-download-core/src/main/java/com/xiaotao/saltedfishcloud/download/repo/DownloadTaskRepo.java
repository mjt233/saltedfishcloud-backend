package com.xiaotao.saltedfishcloud.download.repo;

import com.xiaotao.saltedfishcloud.download.model.DownloadTaskInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

public interface DownloadTaskRepo extends JpaRepository<DownloadTaskInfo, String> {
    @Query("SELECT info FROM DownloadTaskInfo info WHERE info.uid = :uid ORDER BY info.createdAt DESC")
    Page<DownloadTaskInfo> findByUid(@Param("uid") Long uid, Pageable pageable);


    @Query("SELECT info FROM DownloadTaskInfo info WHERE info.uid = :uid AND info.asyncTaskRecord.status IN :status ORDER BY info.createdAt DESC")
    Page<DownloadTaskInfo> findByUidAndState(@Param("uid") Long uid, @Param("status") Collection<Integer> status, Pageable pageable);

    @Query("UPDATE DownloadTaskInfo SET size = :size, name = :name WHERE id = :id")
    @Modifying
    @Transactional
    void updateSizeAndName(@Param("id") String id, @Param("size") Long size, @Param("name") String name);
}
