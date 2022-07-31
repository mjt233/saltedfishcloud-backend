package com.xiaotao.saltedfishcloud.service.wrap;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.constant.error.ShareError;
import com.xiaotao.saltedfishcloud.enums.ArchiveType;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.RedisKeyGenerator;
import com.xiaotao.saltedfishcloud.model.FileTransferInfo;
import com.xiaotao.saltedfishcloud.model.param.WrapParam;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemProvider;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.share.ShareService;
import com.xiaotao.saltedfishcloud.service.share.entity.ShareInfo;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WrapServiceImpl implements WrapService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final NodeService nodeService;
    private final DiskFileSystemProvider fileSystemProvider;

    @Autowired
    private ShareService shareService;

    @Override
    public String registerWrap(Integer uid, FileTransferInfo files) {
        String wid = SecureUtils.getUUID();
        redisTemplate.opsForValue().set(
                RedisKeyGenerator.getWrapKey(wid),
                new WrapInfo(uid, files),
                Duration.ofMinutes(30));
        return wid;
    }

    @Override
    public WrapInfo getWrapInfo(String wid) {
        return (WrapInfo)redisTemplate.opsForValue().get(RedisKeyGenerator.getWrapKey(wid));
    }

    @Override
    public String registerWrap(WrapParam param) {
        String source = param.getSource();

        FileTransferInfo files = new FileTransferInfo();
        String sourceId = param.getSourceId();


        files.setFilenames(param.getFilenames());

        int uid = 0;

        // TODO: 调整为策略模式
        if (source.equals("file")) {
            /* 直接从私人/公共网盘创建的打包 */
            uid = Integer.parseInt(sourceId);
            files.setSource(param.getPath());
        } else if(source.equals("share")){

            /* 从文件分享创建的打包 */
            Map<String, Object> otherData = param.getOtherData();
            // 1. 数据校验
            if (otherData == null) {
                throw new JsonException("缺少otherData");
            }
            String extractCode = TypeUtils.toString(otherData.get("extractCode"));
            String vid = TypeUtils.toString(otherData.get("vid"));
            if (vid == null) {
                throw new JsonException("otherData缺少vid");
            }

            // 2. 获取分享的信息，记录到打包信息中
            ShareInfo shareInfo = shareService.getShare(Integer.parseInt(sourceId), vid);
            if (!shareInfo.validateExtractCode(extractCode)) {
                throw new JsonException(ShareError.SHARE_EXTRACT_ERROR);
            }

            // 3. 获取分享的目录所在的用户网盘路径
            String shareRootPath = nodeService.getPathByNode(shareInfo.getUid(), shareInfo.getNid());

            uid = shareInfo.getUid();
            files.setSource(StringUtils.appendPath(shareRootPath, param.getPath()));
        } else {
            throw new JsonException("不支持的source");
        }
        return this.registerWrap(uid, files);
    }

    @Override
    public void writeWrapToStream(String wid, OutputStream outputStream) throws IOException {

        WrapInfo wrapInfo = getWrapInfo(wid);
        if (wrapInfo == null) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
        FileTransferInfo files = wrapInfo.getFiles();
        fileSystemProvider.getFileSystem().compressAndWriteOut(wrapInfo.getUid(), files.getSource(), files.getFilenames(), ArchiveType.ZIP, outputStream);

    }

    @Override
    public void writeWrapToServlet(String wid, String alias, HttpServletResponse response) throws IOException {
        WrapInfo wrapInfo = getWrapInfo(wid);
        if (wrapInfo == null) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
        if (alias == null) {
            alias = "打包下载" + System.currentTimeMillis() + ".zip";
        }
        FileTransferInfo files = wrapInfo.getFiles();
        response.setHeader(
                ResourceUtils.Header.ContentDisposition,
                ResourceUtils.generateContentDisposition(alias)
        );
        response.setContentType(FileUtils.getContentType("a.ab123c"));
        OutputStream output = response.getOutputStream();
        fileSystemProvider.getFileSystem().compressAndWriteOut(wrapInfo.getUid(), files.getSource(), files.getFilenames(), ArchiveType.ZIP, output);

    }
}
