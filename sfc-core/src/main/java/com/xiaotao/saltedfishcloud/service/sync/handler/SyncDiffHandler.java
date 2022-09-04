package com.xiaotao.saltedfishcloud.service.sync.handler;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.sync.model.FileChangeInfo;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;

/**
 * 同步检测差异结果处理器，用于处理变动的文件或目录。
 */
public interface SyncDiffHandler {
    /**
     * 处理未通过咸鱼云网盘系统在用户目录创建的目录
     * @param uid       用户ID
     * @param paths     目录的完整网盘路径
     */
    void handleDirAdd(int uid, Collection<String> paths) throws IOException, SQLException;


    /**
     * 处理未通过咸鱼云网盘系统在用户目录删除的目录
     * @param uid       用户ID
     * @param paths     目录的完整网盘路径集合，且该集合是<strong>按路径节点长度降序排序的有序集合</strong>
     */
    void handleDirDel(int uid, Collection<String> paths) throws IOException, SQLException;

    /**
     * 处理未通过咸鱼云网盘系统在用户目录创建的文件
     * @param uid       用户ID
     * @param files     新增的文件信息
     */
    void handleFileAdd(int uid, Collection<FileInfo> files) throws IOException, SQLException;

    /**
     * 处理未通过咸鱼云网盘系统在用户目录删除的文件
     * @param uid       用户ID
     * @param files     被删除的文件信息
     */
    void handleFileDel(int uid, Collection<FileInfo> files) throws IOException, SQLException;

    /**
     * 操作用户网盘中，数据库中的文件信息与本地文件信息不一致的文件<br>
     * 出现这个情况通常是因为用户未通过咸鱼云网盘在用户目录修改/覆盖了文件或发生同名文件与目录之间的转换
     * @param uid       用户ID
     * @param files     文件修改更改集合
     */
    void handleFileChange(int uid, Collection<FileChangeInfo> files) throws IOException, SQLException;
}
