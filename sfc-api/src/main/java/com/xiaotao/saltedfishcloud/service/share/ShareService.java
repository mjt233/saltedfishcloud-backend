package com.xiaotao.saltedfishcloud.service.share;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.FileTransferInfo;
import com.xiaotao.saltedfishcloud.model.param.WrapParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareExtractorDTO;
import com.xiaotao.saltedfishcloud.model.po.ShareInfo;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

public interface ShareService {
    /**
     * 创建分享资源的打包码
     * @deprecated 改用 {@link com.xiaotao.saltedfishcloud.service.wrap.WrapService#registerWrap(WrapParam)} 统一创建
     * @param sid                   分享ID
     * @param verification          分享校验码
     * @param code                  分享提取码
     * @param fileTransferInfo      打包信息，dest字段忽略
     * @return                      打包码
     */
    @Deprecated
    String createwrap(Long sid, String verification, String code, FileTransferInfo fileTransferInfo);

    /**
     * 取消分享
     * @param sid 分享ID
     */
    void deleteShare(Long sid, Long uid);

    /**
     * 获取分享文件或目录的具体文件内容资源
     * @param extractor 资源提取信息类
     * @return  文件资源
     */
    Resource getFileResource(ShareExtractorDTO extractor) throws IOException;

    /**
     * 浏览分享的目录文件列表
     * @param sid   分享id
     * @param verification  分享校验码
     * @param path  浏览的路径
     * @param extractCode   提取码
     * @return 一个List数组，数组下标0为目录，1为文件，或null
     * @throws IOException 任何可能的IO异常
     */
    List<FileInfo>[] browse(long sid, String verification, String path, String extractCode) throws IOException;

    /**
     * 创建分享
     * @param uid       用户ID
     * @param shareDTO  分享初始设定数据
     * @return          完整分享信息对象
     */
    ShareInfo createShare(long uid, ShareDTO shareDTO);

    /**
     * 获取用户的所有分享
     * @param uid   用户ID
     * @param page  页码，从1开始
     * @param size  每页大小
     * @param hideKeyAttr 隐藏关键信息
     * @return  分页信息
     */
    CommonPageInfo<ShareInfo> getUserShare(long uid, int page, int size, boolean hideKeyAttr);

    /**
     * 获取分享信息，内部将进行有效性校验，包括：文件是否存在、分享是否存在、校验码是否正确
     * @param sid           分享ID
     * @param verification  校验码
     * @return  分享信息
     */
    ShareInfo getShare(long sid, String verification);

    /**
     * 根据id获取分享信息
     * @param id    分享id
     * @return      分享信息
     */
    ShareInfo getById(long id);
}
