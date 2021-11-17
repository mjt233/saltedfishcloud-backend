package com.xiaotao.saltedfishcloud.service.share;

import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.CommonPageInfo;
import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.share.dao.ShareDao;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareExtractorDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.SharePO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareType;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final UserDao userDao;
    private final DiskFileSystemFactory fileSystemFactory;

    /**
     * 取消分享
     * @param sid 分享ID
     */
    public void deleteShare(Integer sid, Integer uid) {
        SharePO share = shareDao.findById(sid).orElse(null);
        if (share == null) throw new JsonException(ErrorInfo.SHARE_NOT_FOUND);
        if (!share.getUid().equals(uid)) throw new JsonException(ErrorInfo.SYSTEM_FORBIDDEN);
        shareDao.deleteById(sid);
    }

    /**
     * 获取分享文件或目录的具体文件内容资源
     * @param extractor 资源提取信息类
     * @return  文件资源
     */
    public Resource getFileResource(ShareExtractorDTO extractor) {
        SharePO share = shareDao.findById(extractor.getSid()).orElse(null);
        if (share == null) throw new JsonException(ErrorInfo.SHARE_NOT_FOUND);
        if (!share.getVerification().equals(extractor.getVerification())) throw new JsonException(ErrorInfo.SHARE_NOT_FOUND);
        if (!share.validateExtractCode(extractor.getCode())) throw new JsonException(ErrorInfo.SHARE_EXTRACT_ERROR);

        // 文件直接获取Resource
        String basePath = nodeService.getPathByNode(share.getUid(), share.getParentId());
        if (share.getType() == ShareType.FILE) {
            return fileSystemFactory.getFileSystem().getResource(
                    share.getUid(),
                    basePath,
                    share.getName()
                );
        }
        String fullPath = PathBuilder.formatPath(basePath + "/" + extractor.getPath(), true);
        if (!FileNameValidator.valid(extractor.getName())) throw new IllegalArgumentException("无效文件名");
        if (!fullPath.startsWith(basePath)) throw new IllegalArgumentException("无效路径");
        return fileSystemFactory.getFileSystem().getResource(
                share.getUid(),
                fullPath,
                extractor.getName()
        );
    }

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
                    fileInfo.getMd5(),
                    uid
            );
            sharePO.setParentId(nid);
            sharePO.setSize(fileInfo.getSize());
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
    public CommonPageInfo<SharePO> getUserShare(int uid, int page, int size, boolean hideKeyAttr) {
        SharePO po = new SharePO();
        po.setUid(uid);
        CommonPageInfo<SharePO> res = CommonPageInfo.of(
                shareDao.findAll(
                        Example.of(po, ExampleMatcher.matching()),
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.DESC, "createdAt")
                        )
                )
        );
//        CommonPageInfo<SharePO> res = CommonPageInfo.of(shareDao.findAllByUidEquals(
//                uid,
//                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
//        ));
        if (hideKeyAttr) {
            for (SharePO share : res.getContent()) {
                share.hideKeyAttr();
            }
        }
        return res;
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
        if (po.isExpired()) throw new JsonException(ErrorInfo.SHARE_EXTRACT_ERROR);

        User user = userDao.getUserById(po.getUid());
        if (user != null) po.setUsername(user.getUsername());
        return po;
    }

}
