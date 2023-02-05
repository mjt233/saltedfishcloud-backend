package com.xiaotao.saltedfishcloud.download.repo;

import com.xiaotao.saltedfishcloud.download.model.DownloadTaskInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface DownloadTaskRepo extends JpaRepository<DownloadTaskInfo, String> {
    Page<DownloadTaskInfo> findByUidOrderByCreatedAtDesc(int uid, Pageable pageable);

    Page<DownloadTaskInfo> findByUidAndStateInOrderByCreatedAtDesc(int uid, Collection<DownloadTaskInfo.State> acceptState, Pageable pageable);
}
