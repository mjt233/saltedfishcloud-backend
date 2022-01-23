package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.entity.po.DownloadTaskInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface DownloadTaskRepo extends JpaRepository<DownloadTaskInfo, String> {
    Page<DownloadTaskInfo> findByUidOrderByCreatedAtDesc(int uid, Pageable pageable);

    Page<DownloadTaskInfo> findByUidAndStateInOrderByCreatedAtDesc(int uid, Collection<DownloadTaskInfo.State> acceptState, Pageable pageable);
}
