package com.xiaotao.saltedfishcloud.service.download;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseExtractor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class DownloadExtractor implements ResponseExtractor<File>, ProgressExtractor {
    private long total;
    private long loaded;
    private final Path savePath;
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
    public File extractData(ClientHttpResponse response) throws IOException {
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
        return savePath.toFile();
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
