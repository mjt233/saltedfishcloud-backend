package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.po.DownloadTaskInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DownloadTaskRepository extends JpaRepository<DownloadTaskInfo, String> {
    Page<DownloadTaskInfo> findByUidOrderByCreatedAtDesc(int uid, Pageable pageable);
}
