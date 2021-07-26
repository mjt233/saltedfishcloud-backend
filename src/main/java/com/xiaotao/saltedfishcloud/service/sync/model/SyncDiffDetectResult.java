package com.xiaotao.saltedfishcloud.service.sync.model;

import com.xiaotao.saltedfishcloud.po.file.FileInfo;

import java.util.List;

/**
 * 同步检测后的数据差异结果集合
 */
public interface SyncDiffDetectResult {

    /**
     * 未通过咸鱼云网盘系统删除的目录，若无，则为空列表，不得返回null
     */
    List<String> getDeletedDirPaths();

    /**
     * 未通过咸鱼云网盘系统删除的文件，若无，则为空列表，不得返回null
     */
    List<FileInfo> getDeletedFiles() ;

    /**
     * 未通过咸鱼云网盘系统新增的文件，若无，则为空列表，不得返回null
     */
    List<FileInfo> getNewFiles() ;
    /**
     * 未通过咸鱼云网盘系统新增的目录，若无，则为空列表，不得返回null
     */
    List<String> getNewDirPaths();

    /**
     * 未通过咸鱼云网盘系统更改的文件，若无，则为空列表，不得返回null
     */
    List<FileChangeInfo> getChangeFiles();
}
