package com.sfc.download.config;

import com.sfc.download.model.property.Aria2Property;
import com.sfc.download.model.property.GitProperty;

/**
 * 下载服务运行期配置访问入口
 *
 * @see Aria2Property
 * @see GitProperty
 */
public interface SfcDownloadConfigService {

    /**
     * 获取 aria2（P2P 下载）的当前配置快照。
     *
     * @return aria2 配置，永不为 {@code null}；core 不可用时返回默认值
     */
    Aria2Property getAria2();

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
     * 「改完 core 配置后立即验证」的场景。</p>
     */
    void refresh();
}
