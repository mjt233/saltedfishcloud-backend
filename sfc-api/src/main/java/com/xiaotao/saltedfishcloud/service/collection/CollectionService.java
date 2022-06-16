package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.model.dto.CollectionDTO;
import com.xiaotao.saltedfishcloud.model.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfoId;
import com.xiaotao.saltedfishcloud.model.po.CollectionRecord;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;

public interface CollectionService {
    /**
     * 获取某个文件收集接收到的文件记录
     * @param cid   收集ID
     * @param page  页码，从0开始
     * @param size  每页的大小
     * @return      分页信息
     */
    Page<CollectionRecord> getSubmits(Long cid, int page, int size);

    /**
     * 删除一个文件收集
     * @param uid   调用者用户ID，用于验证权限
     * @param cid   文件收集ID
     */
    void deleteCollection(int uid, long cid);

    /**
     * 关闭一个收集任务，停止收集
     * @param uid   调用者用户ID
     * @param cid   收集ID
     * @return  关闭后的收集任务信息
     */
    CollectionInfo setState(int uid, Long cid, CollectionInfo.State state);

    /**
     * 创建收集任务
     * @param uid   创建者ID
     * @param info  收集任务信息
     * @return      收集任务ID
     */
    CollectionInfoId createCollection(int uid, CollectionDTO info);

    /**
     * 获取一个收集信息
     * @param cid   收集ID
     * @return      收集信息，若收集id不存在，则返回null
     */
    CollectionInfo getCollection(Long cid);

    /**
     * 获取一个收集信息，此方法要求同时使用ID和验证码
     * @param cid   收集ID
     * @return      收集信息，若收集id不存在，则返回null
     */
    CollectionInfo getCollectionWitchVerification(CollectionInfoId cid);

    /**
     * 接受一个文件存到集合中
     * @param cid   收集ID
     * @param uid   提供者ID，游客使用0
     * @param is    文件输入流
     * @param fileInfo      基础文件信息（至少应包含文件名，大小，md5）
     * @param ip            提交者的IP地址
     * @param submitFile    提交的文件信息
     */
    @Transactional(rollbackFor = Throwable.class)
    void collectFile(CollectionInfoId cid, int uid, InputStream is, FileInfo fileInfo, SubmitFile submitFile, String ip) throws IOException;
}
