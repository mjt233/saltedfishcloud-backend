package com.xiaotao.saltedfishcloud.service.download;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseExtractor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class HttpResourceFile extends File {
    @Getter
    private String resourceName;

    public HttpResourceFile(String nativePath, String resourceName) {
        super(nativePath);
        this.resourceName = resourceName;
    }

    public HttpResourceFile(String pathname) {
        super(pathname);
    }
}

@Slf4j
public class DownloadExtractor implements ResponseExtractor<HttpResourceFile>, ProgressExtractor {
    private long total;
    private long loaded;
    private final Path savePath;
    @Getter
    private String resourceName;
    public DownloadExtractor(Path savePath) {
        this.savePath = savePath;
    }
    public DownloadExtractor(String savePath) {
        this.savePath = Paths.get(savePath);
    }
    public DownloadExtractor(File saveFile) {
        this.savePath = saveFile.toPath();
    }

    @Override
    public HttpResourceFile extractData(ClientHttpResponse response) throws IOException {
        var parent = savePath.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        // 文件名探测
        resourceName = response.getHeaders().getContentDisposition().getFilename();
        if (resourceName != null) {
            log.debug("从响应头探测到文件名：" + resourceName);
        } else {
            log.debug("响应头无文件名");
        }

        // 检测本地文件目录是否正常可写
        if (Files.exists(savePath) && Files.isDirectory(savePath)) {
            throw new IOException(savePath + "为已存在的目录");
        }

        // 开始下载
        InputStream body = response.getBody();
        total = response.getHeaders().getContentLength();
        log.debug("开始下载，文件保存到:{}", savePath);
        OutputStream localFileStream = Files.newOutputStream(savePath);
        byte[] buffer = new byte[8192];
        int cnt;
        while ( (cnt = body.read(buffer)) != -1 ) {
            loaded += cnt;
            localFileStream.write(buffer, 0, cnt);
        }

        // 下载完毕，构造本地文件信息
        var res = new HttpResourceFile(savePath.toString(), resourceName);
        if (res.length() != total) {
            total = res.length();
            log.debug("实际获取的总大小：" + total);
        }
        return res;
    }

    @Override
    public long getTotal() {
        return total;
    }

    @Override
    public long getLoaded() {
        return loaded;
    }
}
