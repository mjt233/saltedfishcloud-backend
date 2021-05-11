package com.xiaotao.saltedfishcloud.service.http;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ResponseService {
    @Resource
    private NodeService nodeService;
    @Resource
    private FileDao fileDao;


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
        return ResponseEntity.ok()
                .header("Content-Type", FileUtils.getContentType(name))
                .header("Content-Disposition", "inline;filename="+ URLEncoder.encode(name, "utf-8"))
                .body(urlResource);
    }
}
