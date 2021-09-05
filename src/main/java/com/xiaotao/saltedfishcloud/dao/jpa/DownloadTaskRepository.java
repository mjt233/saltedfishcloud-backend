package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.po.DownloadTaskInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadTaskRepository extends JpaRepository<DownloadTaskInfo, String> {

}
