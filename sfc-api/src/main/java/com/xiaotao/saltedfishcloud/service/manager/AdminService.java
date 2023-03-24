package com.xiaotao.saltedfishcloud.service.manager;

import com.xiaotao.saltedfishcloud.model.SystemInfoVO;
import com.xiaotao.saltedfishcloud.model.TimestampRecord;
import com.xiaotao.saltedfishcloud.model.vo.SystemOverviewVO;

import java.util.Collection;

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

    /**
     * 记录一笔系统信息。
     */
    void addSystemInfoRecord();

    /**
     * 清除已记录的系统信息
     */
    void cleanSystemInfo();

    /**
     * 获取系统一段时间范围内的信息
     */
    Collection<TimestampRecord<SystemInfoVO>> listSystemInfo();

    /**
     * 获取系统当前信息
     * @param full  是否获取全部完整数据（若为false则只获取cpu和内存）
     */
    SystemInfoVO getCurSystemInfo(boolean full);
}
