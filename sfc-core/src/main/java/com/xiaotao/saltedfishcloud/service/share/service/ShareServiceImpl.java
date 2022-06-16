package com.xiaotao.saltedfishcloud.service.share.service;

import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.constant.error.ShareError;
import com.xiaotao.saltedfishcloud.dao.jpa.ShareRepo;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.FileTransferInfo;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemProvider;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.share.ShareService;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareExtractorDTO;
import com.xiaotao.saltedfishcloud.service.share.entity.SharePO;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareType;
import com.xiaotao.saltedfishcloud.service.wrap.WrapService;
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
import java.net.URLDecoder;
import java.nio.file.NoSuchFileException;
import java.util.LinkedList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {
    private final NodeService nodeService;
    private final FileDao fileDao;
    private final ShareRepo shareRepo;
    private final UserDao userDao;
    private final DiskFileSystemProvider fileSystemFactory;
    private final WrapService wrapService;

    @Override
    public String createwrap(Integer sid, String verification, String code, FileTransferInfo fileTransferInfo) {
        SharePO share = getShare(sid, verification);
        if (share.getExtractCode() != null && !code.equalsIgnoreCase(share.getExtractCode())) {
            throw new JsonException(ShareError.SHARE_EXTRACT_ERROR);
        }
        if (share.getType() != ShareType.DIR) throw new JsonException(400, "只能对文件夹分享进行打包");

        String path = nodeService.getPathByNode(share.getUid(), share.getNid());
        fileTransferInfo.setSource(path + fileTransferInfo.getSource());

        return wrapService.registerWrap(share.getUid(), fileTransferInfo);
    }

    @Override
    public void deleteShare(Integer sid, Integer uid) {
        SharePO share = shareRepo.findById(sid).orElse(null);
        if (share == null) throw new JsonException(ShareError.SHARE_NOT_FOUND);
        if (!share.getUid().equals(uid)) throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        shareRepo.deleteById(sid);
    }

    @Override
    public Resource getFileResource(ShareExtractorDTO extractor) throws IOException {
        SharePO share = shareRepo.findById(extractor.getSid()).orElse(null);
        if (share == null) throw new JsonException(ShareError.SHARE_NOT_FOUND);
        if (!share.getVerification().equals(extractor.getVerification())) throw new JsonException(ShareError.SHARE_NOT_FOUND);
        if (!share.validateExtractCode(extractor.getCode())) throw new JsonException(ShareError.SHARE_EXTRACT_ERROR);

        // 文件直接获取Resource
        String basePath = nodeService.getPathByNode(share.getUid(), share.getParentId());
        if (share.getType() == ShareType.FILE) {
            return fileSystemFactory.getFileSystem().getResource(
                    share.getUid(),
                    basePath,
                    share.getName()
                );
        }
        String fullPath = PathBuilder.formatPath(basePath + "/" + share.getName() + "/" + extractor.getPath(), true);
        if (!FileNameValidator.valid(extractor.getName())) throw new IllegalArgumentException("无效文件名");
        if (!fullPath.startsWith(basePath)) throw new IllegalArgumentException("无效路径");
        return fileSystemFactory.getFileSystem().getResource(
                share.getUid(),
                fullPath,
                URLDecoder.decode(extractor.getName(), "UTF-8")
        );
    }

    @Override
    public List<FileInfo>[] browse(int sid, String verification, String path, String extractCode) throws IOException {
        SharePO share = shareRepo.findById(sid).orElse(null);

        // 校验存在
        if (share == null) throw new JsonException(ShareError.SHARE_NOT_FOUND);

        // 校验校验码
        if (!share.getVerification().equals(verification)) throw new JsonException(ShareError.SHARE_NOT_FOUND);

        // 校验类型
        if (share.getType() == ShareType.FILE) throw new IllegalArgumentException("仅接受文件类型的分享");

        // 校验提取码
        if (StringUtils.hasLength(share.getExtractCode()) && !share.getExtractCode().equalsIgnoreCase(extractCode)) {
            throw new JsonException(ShareError.SHARE_EXTRACT_ERROR);
        }

        String basePath = nodeService.getPathByNode(share.getUid(), share.getNid());
        String fullPath = PathBuilder.formatPath(basePath + "/" + path, true);
        if ( !fullPath.startsWith(basePath)) throw new IllegalArgumentException("非法参数");



        try {
            return fileSystemFactory.getFileSystem().getUserFileList(share.getUid(), fullPath);
        } catch (NoSuchFileException e) { throw new JsonException(FileSystemError.NODE_NOT_FOUND); }
    }

    @Override
    public SharePO createShare(int uid, ShareDTO shareDTO) {
        try {
            LinkedList<NodeInfo> nodes = nodeService.getPathNodeByPath(uid, shareDTO.getPath());
            String nid = nodes.getLast().getId();
            FileInfo fileInfo = fileDao.getFileInfo(uid, shareDTO.getName(), nid);

            if (fileInfo == null) throw new JsonException(FileSystemError.FILE_NOT_FOUND);
            SharePO sharePO = SharePO.valueOf(
                    shareDTO,
                    fileInfo.isFile() ? ShareType.FILE : ShareType.DIR,
                    fileInfo.getMd5(),
                    uid
            );
            sharePO.setParentId(nid);
            sharePO.setSize(fileInfo.getSize());
            shareRepo.save(sharePO);
            return sharePO;
        } catch (NoSuchFileException e) {
            throw new JsonException(FileSystemError.NODE_NOT_FOUND);
        }
    }

    @Override
    public CommonPageInfo<SharePO> getUserShare(int uid, int page, int size, boolean hideKeyAttr) {
        SharePO po = new SharePO();
        po.setUid(uid);
        CommonPageInfo<SharePO> res = CommonPageInfo.of(
                shareRepo.findAll(
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

    @Override
    public SharePO getShare(int sid, String verification) {
        SharePO po = shareRepo.findById(sid).orElse(null);
        if (po == null) throw new JsonException(ShareError.SHARE_NOT_FOUND);

        if (po.getType() == ShareType.DIR) {
            // 判断原分享目录是否失效
            String nid = po.getNid();
            NodeInfo nodeInfo = nodeService.getNodeById(po.getUid(), nid);
            if (nodeInfo == null) throw new JsonException(ShareError.SHARE_NOT_FOUND);
        } else {
            // 判断分享的原文件是否失效
            FileInfo fi = fileDao.getFileInfo(po.getUid(), po.getName(), po.getParentId());
            if (fi == null) throw new JsonException(ShareError.SHARE_NOT_FOUND);
        }

        if (!po.getVerification().equals(verification)) throw new JsonException(ShareError.SHARE_NOT_FOUND);
        if (po.isExpired()) throw new JsonException(ShareError.SHARE_NOT_FOUND);

        User user = userDao.getUserById(po.getUid());
        if (user != null) po.setUsername(user.getUsername());
        return po;
    }

}
