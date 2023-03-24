package com.xiaotao.saltedfishcloud.service.manager;

import com.xiaotao.saltedfishcloud.model.vo.SystemOverviewVO;

public interface AdminService {
    /**
     * 获取系统的预览数据
     *
     * @return
     */
    SystemOverviewVO getOverviewData();

    /**
     * 重启系统
     * @param withCluster 是否整个集群重启
     */
    void restart(boolean withCluster);
}
