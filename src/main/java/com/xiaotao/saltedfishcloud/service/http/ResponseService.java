package com.xiaotao.saltedfishcloud.service.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.entity.po.file.FileDCInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class ResponseService {
    private final NodeService nodeService;
    private final FileDao fileDao;


    /**
     * 获取用户网盘文件的资源响应
     * @param uid       用户ID
     * @param filePath  请求的文件网盘中的完整路径
     * @return          资源响应
     */
    public ResponseEntity<org.springframework.core.io.Resource> getUserFileResponse(int uid, String filePath) throws NoSuchFileException, MalformedURLException, UnsupportedEncodingException {
        PathBuilder pathBuilder = new PathBuilder();
        pathBuilder.append(filePath);
        String name = pathBuilder.getPath().getLast();
        String dir = pathBuilder.range(-1);
        String nid = nodeService.getLastNodeInfoByPath(uid, dir).getId();
        FileInfo fileInfo = fileDao.getFileInfo(uid, name, nid);
        if (fileInfo == null) throw new NoSuchFileException("文件不存在");
        return sendFile(DiskConfig.getPathHandler().getStorePath(uid, dir, fileInfo), fileInfo.getName());
    }

    /**
     * 向客户端响应一个文件内容
     * @param localFilePath     本地文件路径
     * @return  响应实体
     */
    public ResponseEntity<org.springframework.core.io.Resource> sendFile(String localFilePath) throws MalformedURLException, UnsupportedEncodingException {
        return sendFile(localFilePath, localFilePath.substring(localFilePath.lastIndexOf('/') + 1) );
    }

    /**
     * 向客户端响应一个文件内容
     * @param localFilePath     本地文件路径
     * @param name              文件响应重命名
     * @return  响应实体
     */
    public ResponseEntity<org.springframework.core.io.Resource> sendFile(String localFilePath, String name) throws MalformedURLException, UnsupportedEncodingException {
        Path path = Paths.get(localFilePath);
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("无法直接下载文件夹");
        }
        UrlResource urlResource = new UrlResource(path.toUri());
        String disposition = "inline;filename*=UTF-8''"+ URLEncoder.encode(name, "utf-8");
        return ResponseEntity.ok()
                .header("Content-Type", FileUtils.getContentType(name))
                .header("Content-Disposition", disposition)
                .body(urlResource);
    }


    /**
     * 通过下载码获取资源响应体
     * @param dc 下载码
     * @return  资源响应体
     */
    public ResponseEntity<org.springframework.core.io.Resource> getResourceByDC(String dc, boolean directDownload) throws MalformedURLException, UnsupportedEncodingException {
        FileDCInfo info;
        try {
            String data = JwtUtils.parse(dc);
            info = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false).readValue(data, FileDCInfo.class);
        } catch (JsonProcessingException e) {
            throw new JsonException(400, "下载码无效");
        }
        Path localFilePath = Paths.get(DiskConfig.getPathHandler().getStorePath(info.getUid(), info.getDir(), info));
        String name = info.getName();
        UrlResource urlResource = new UrlResource(localFilePath.toUri());
        String ct = FileUtils.getContentType(directDownload ? "a" : name);
        String disposition = "inline;filename*=UTF-8''"+ URLEncoder.encode(name, "utf-8");
        return ResponseEntity.ok()
                .header("Content-Type", ct)
                .header("Content-Disposition", disposition)
                .body(urlResource);
    }

    public ResponseEntity<Resource> wrapResource(Resource resource) throws UnsupportedEncodingException {
        String name = resource.getFilename();
        assert name != null;
        String disposition = "inline;filename*=UTF-8''"+ URLEncoder.encode(name, "utf-8");
        String ct = FileUtils.getContentType(name);
        return ResponseEntity.ok()
                .header("Content-Type", ct)
                .header("Content-Disposition", disposition)
                .body(resource);
    }
}
