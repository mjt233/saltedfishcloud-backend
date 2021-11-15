package com.xiaotao.saltedfishcloud.service.share;

import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.entity.CommonPageInfo;
import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.share.dao.ShareDao;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.SharePO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.LinkedList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShareService {
    private final NodeService nodeService;
    private final FileDao fileDao;
    private final ShareDao shareDao;
    private final DiskFileSystemFactory fileSystemFactory;

    public List<FileInfo>[] browse(int sid, String verification, String path, String extractCode) throws IOException {
        SharePO share = shareDao.findById(sid).orElse(null);

        // 校验存在
        if (share == null) throw new JsonException(ErrorInfo.SHARE_NOT_FOUND);

        // 校验校验码
        if (!share.getVerification().equals(verification)) throw new JsonException(ErrorInfo.SHARE_NOT_FOUND);

        // 校验类型
        if (share.getType() == ShareType.FILE) throw new IllegalArgumentException("仅接受文件类型的分享");

        // 校验提取码
        if (StringUtils.hasLength(share.getExtractCode()) && !share.getExtractCode().equals(extractCode)) {
            throw new JsonException(ErrorInfo.SHARE_EXTRACT_ERROR);
        }

        String basePath = nodeService.getPathByNode(share.getUid(), share.getNid());
        String fullPath = PathBuilder.formatPath(basePath + "/" + path, true);
        if ( !fullPath.startsWith(basePath)) throw new IllegalArgumentException("非法参数");



        try {
            nodeService.getPathNodeByPath(share.getUid(), path);
            return fileSystemFactory.getFileSystem().getUserFileList(share.getUid(), fullPath);
        } catch (NoSuchFileException e) { throw new JsonException(ErrorInfo.NODE_NOT_FOUND); }
    }

    /**
     * 创建分享
     * @param uid       用户ID
     * @param shareDTO  分享初始设定数据
     * @return          完整分享信息对象
     */
    public SharePO createShare(int uid, ShareDTO shareDTO) {
        try {
            LinkedList<NodeInfo> nodes = nodeService.getPathNodeByPath(uid, shareDTO.getPath());
            String nid = nodes.getLast().getId();
            FileInfo fileInfo = fileDao.getFileInfo(uid, shareDTO.getName(), nid);

            if (fileInfo == null) throw new JsonException(ErrorInfo.FILE_NOT_FOUND);
            SharePO sharePO = SharePO.valueOf(
                    shareDTO,
                    fileInfo.isFile() ? ShareType.FILE : ShareType.DIR,
                    fileInfo.isFile()? fileInfo.getMd5() : nid,
                    uid
            );
            shareDao.save(sharePO);
            return sharePO;
        } catch (NoSuchFileException e) {
            throw new JsonException(ErrorInfo.NODE_NOT_FOUND);
        }
    }

    /**
     * 获取用户的所有分享
     * @param uid   用户ID
     * @param page  页码，从1开始
     * @param size  每页大小
     * @return  分页信息
     */
    public CommonPageInfo<SharePO> getUserShare(int uid, int page, int size) {
        return CommonPageInfo.of(shareDao.findAllByUidEquals(uid, PageRequest.of(page, size)));
    }

    /**
     * 获取分享信息
     * @param sid           分享ID
     * @param verification  校验码
     * @return  分享信息
     */
    public SharePO getShare(int sid, String verification) {
        SharePO po = shareDao.findById(sid).orElse(null);
        if (po == null) throw new JsonException(ErrorInfo.SHARE_NOT_FOUND);
        if (!po.getVerification().equals(verification)) throw new JsonException(ErrorInfo.SHARE_NOT_FOUND);
        return po;
    }

}
