package com.sfc.download.config;

import com.sfc.download.model.property.GitProperty;

/**
 * 下载服务运行期业务配置访问入口。
 *
 * <p>只负责读取 {@code sfc-core} 统一配置表中由管理员在后台「插件配置」面板维护的<b>业务参数</b>
 * （当前为 Git 克隆默认参数）。aria2 的全部参数属于部署期配置，见
 * {@link Aria2Properties}，不在本接口范围内。</p>
 *
 * @see GitProperty
 * @see Aria2Properties
 */
public interface SfcDownloadConfigService {

    /**
     * 获取 Git 克隆的当前配置快照。
     *
     * @return Git 配置，永不为 {@code null}；core 不可用时返回默认值
     */
    GitProperty getGit();

    /**
     * 强制刷新配置快照。
     *
     * <p>正常流程无需调用（读取时会按 TTL 自动刷新）；该方法用于排障，或测试中
     * 「改完 core 配置后立即验证」的场景。aria2 为部署期配置，修改后需重启本服务，本方法对其无效。</p>
     */
    void refresh();
}
