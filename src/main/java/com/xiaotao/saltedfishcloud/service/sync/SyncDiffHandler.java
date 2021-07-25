package com.xiaotao.saltedfishcloud.service.sync;

import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;

import java.util.Collection;


public interface SyncDiffHandler {
    /**
     * 处理未通过咸鱼云网盘系统在用户目录创建的目录
     * @param user      用户信息
     * @param paths     目录的完整网盘路径
     */
    void handleDirAdd(User user, Collection<String> paths) throws Exception;


    /**
     * 处理未通过咸鱼云网盘系统在用户目录删除的目录
     * @param user      用户信息
     * @param paths     目录的完整网盘路径集合，且该集合是<strong>按路径节点长度降序排序的有序集合</strong>
     */
    void handleDirDel(User user, Collection<String> paths) throws Exception;

    /**
     * 处理未通过咸鱼云网盘系统在用户目录创建的文件
     * @param user      用户信息
     * @param files     新增的文件信息
     */
    void handleFileAdd(User user, Collection<FileInfo> files) throws Exception;

    /**
     * 处理未通过咸鱼云网盘系统在用户目录删除的文件
     * @param user      用户信息
     * @param files     被删除的文件信息
     */
    void handleFileDel(User user, Collection<FileInfo> files) throws Exception;

    /**
     * 操作用户网盘中，数据库中的文件信息与本地文件信息不一致的文件<br>
     * 出现这个情况通常是因为用户未通过咸鱼云网盘在用户目录修改/覆盖了文件或发生同名文件与目录之间的转换
     * @param user      用户信息
     * @param files     文件修改更改集合
     */
    void handleFileChange(User user, Collection<FileChangeInfo> files) throws Exception;
}
