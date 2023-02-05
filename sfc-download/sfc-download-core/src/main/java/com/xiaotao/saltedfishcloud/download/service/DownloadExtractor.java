package com.xiaotao.saltedfishcloud.download.service;

import com.xiaotao.saltedfishcloud.common.prog.ProgressProvider;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.service.async.context.AsyncTackCallback;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import lombok.Getter;
import lombok.Setter;
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

/**
 * @TODO 基于org.springframework.core.io.Resource实现一个更通用的文件资源实体类
 */
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
public class DownloadExtractor implements ResponseExtractor<HttpResourceFile>, ProgressProvider {
    private final static String LOG_TITLE = "[Download Extractor]";

    private final ProgressRecord progressRecord;
    private final Path savePath;
    private boolean isFinish = false;

    @Getter
    private boolean interrupted = false;

    @Setter
    private AsyncTackCallback readyCallback;

    @Getter
    private String resourceName;



    public DownloadExtractor(File saveFile) {
        this(saveFile.toPath());
    }
    public DownloadExtractor(String savePath) {
        this(Paths.get(savePath));
    }
    public DownloadExtractor(Path savePath) {
        this.savePath = savePath;
        this.progressRecord = new ProgressRecord();
    }

    public void interrupt() {
        interrupted = true;
    }

    @Override
    public HttpResourceFile extractData(ClientHttpResponse response) throws IOException {
        Path parent = savePath.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        // 文件名探测
        resourceName = response.getHeaders().getContentDisposition().getFilename();
        if (resourceName != null && FileNameValidator.valid(resourceName)) {
            log.debug("{}从响应头探测到文件名：{}", LOG_TITLE, resourceName);
        } else {
            log.debug("{}响应头无文件名", LOG_TITLE);
        }

        // 检测本地文件目录是否正常可写
        if (Files.exists(savePath) && Files.isDirectory(savePath)) {
            throw new IOException(savePath + "为已存在的目录");
        }

        // 开始下载
        InputStream body = response.getBody();
        progressRecord.setTotal(response.getHeaders().getContentLength());
        progressRecord.setLoaded(0);
        log.debug("{}开始下载，文件保存到:{}",LOG_TITLE, savePath);
        OutputStream localFileStream = Files.newOutputStream(savePath);
        byte[] buffer = new byte[8192];
        int cnt;

        readyCallback.action();
        try {
            while ( (cnt = body.read(buffer)) != -1 ) {
                // 中断信号检测
                if (interrupted) {
                    body.close();
                    localFileStream.close();
                    if (Files.exists(savePath)) {
                        Files.delete(savePath);
                    }
                    log.debug("{}下载被中断:{}", LOG_TITLE, resourceName);
                    return null;
                }
                progressRecord.appendLoaded(cnt);
                // 写入小块文件
                localFileStream.write(buffer, 0, cnt);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            isFinish = true;
            body.close();
            localFileStream.close();
        }

        // 下载完毕，构造本地文件信息
        HttpResourceFile res = new HttpResourceFile(savePath.toString(), resourceName);
        if (res.length() != progressRecord.getTotal()) {
            progressRecord.setTotal(res.length());
        }
        return res;
    }

    @Override
    public ProgressRecord getProgressRecord() {
        return progressRecord;
    }

    @Override
    public boolean isStop() {
        return isFinish;
    }
}
