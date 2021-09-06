package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.po.DownloadTaskInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DownloadTaskRepository extends JpaRepository<DownloadTaskInfo, String> {
    List<DownloadTaskInfo> findByUidOrderByCreatedAtDesc(int uid);
}
