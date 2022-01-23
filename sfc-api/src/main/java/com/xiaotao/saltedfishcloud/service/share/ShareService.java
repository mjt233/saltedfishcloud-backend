package com.xiaotao.saltedfishcloud.service.share;

import com.xiaotao.saltedfishcloud.entity.CommonPageInfo;
import com.xiaotao.saltedfishcloud.entity.FileTransferInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareExtractorDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.SharePO;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

public interface ShareService {
    /**
     * 创建分享资源的打包码
     * @param sid                   分享ID
     * @param verification          分享校验码
     * @param code                  分享提取码
     * @param fileTransferInfo      打包信息，dest字段忽略
     * @return                      打包码
     */
    String createwrap(Integer sid, String verification, String code, FileTransferInfo fileTransferInfo);

    /**
     * 取消分享
     * @param sid 分享ID
     */
    void deleteShare(Integer sid, Integer uid);

    /**
     * 获取分享文件或目录的具体文件内容资源
     * @param extractor 资源提取信息类
     * @return  文件资源
     */
    Resource getFileResource(ShareExtractorDTO extractor) throws UnsupportedEncodingException;

    List<FileInfo>[] browse(int sid, String verification, String path, String extractCode) throws IOException;

    /**
     * 创建分享
     * @param uid       用户ID
     * @param shareDTO  分享初始设定数据
     * @return          完整分享信息对象
     */
    SharePO createShare(int uid, ShareDTO shareDTO);

    /**
     * 获取用户的所有分享
     * @param uid   用户ID
     * @param page  页码，从1开始
     * @param size  每页大小
     * @param hideKeyAttr 隐藏关键信息
     * @return  分页信息
     */
    CommonPageInfo<SharePO> getUserShare(int uid, int page, int size, boolean hideKeyAttr);

    /**
     * 获取分享信息
     * @param sid           分享ID
     * @param verification  校验码
     * @return  分享信息
     */
    SharePO getShare(int sid, String verification);
}
