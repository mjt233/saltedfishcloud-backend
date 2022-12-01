package com.xiaotao.saltedfishcloud.service.manager;

import com.xiaotao.saltedfishcloud.model.vo.SystemOverviewVO;

public interface AdminService {
    /**
     * 获取系统的预览数据
     *
     * @return
     */
    SystemOverviewVO getOverviewData();
}
