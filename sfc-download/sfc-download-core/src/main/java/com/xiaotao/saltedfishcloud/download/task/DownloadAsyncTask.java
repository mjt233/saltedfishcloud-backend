package com.xiaotao.saltedfishcloud.download.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.download.IgnoreSSLHttpRequestFactory;
import com.xiaotao.saltedfishcloud.download.model.DownloadProgressRecord;
import com.xiaotao.saltedfishcloud.download.repo.DownloadTaskRepo;
import com.xiaotao.saltedfishcloud.helper.CustomLogger;
import com.xiaotao.saltedfishcloud.download.model.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import lombok.Setter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class DownloadAsyncTask implements AsyncTask {
    private final String originParams;

    private final DownloadTaskParams params;

    private CustomLogger logger;

    private String filename;

    private RestTemplate restTemplate;

    private Proxy sysProxy;

    private HttpHeaders headers;

    private Path tempFilePath;

    /**
     * 是否取消下载
     */
    private boolean isCancel = false;

    /**
     * 是否已完成（无论失败还是成功）
     */
    private boolean isFinish = false;

    /**
     * 是否已开始
     */
    private boolean isStart = false;

    /**
     * 接收文件时的输入流
     */
    private InputStream receiverInputStream;

    @Setter
    private ProxyDao proxyDao;

    @Setter
    private NodeService nodeService;

    @Setter
    private DiskFileSystem diskFileSystem;

    @Setter
    private DownloadTaskRepo downloadTaskRepo;

    private final DownloadProgressRecord progressRecord = new DownloadProgressRecord();

    {
        progressRecord.setFilename("unknown");
        progressRecord.setLoaded(0);
        progressRecord.setTotal(-1);
    }

    public DownloadAsyncTask(String originParams) {
        try {
            this.originParams = originParams;
            this.params = MapperHolder.parseJson(originParams, DownloadTaskParams.class);

        } catch (IOException e) {
            throw new IllegalArgumentException("参数解析失败", e);
        }

    }

    @Override
    public void execute(OutputStream logOutputStream) {
        if (isStart) {
            throw new IllegalArgumentException("重复执行");
        }
        try {
            logger = new CustomLogger(logOutputStream);
            isStart = true;

            // 校验文件保存路径
            validPath();

            // 初始化header
            initHeader();

            // 初始化代理对象
            initProxy();

            // 初始化RestTemplate对象 配置代理、ssl等属性
            initRestTemplate();

            // 初始化默认文件名
            initFilename();

            // 发起请求开始下载，创建本地临时文件(主要阻塞方法)
            startDownload();

            // 保存下载的文件
            saveFile();
        } catch (Exception e) {
            isStart = false;
            isFinish = true;
            if (isCancel) {
                logger.error("下载被取消导致中断");
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }

        } finally {
            if (tempFilePath != null && Files.exists(tempFilePath)) {
                logger.info("清理临时文件: " + tempFilePath);
                try {
                    Files.deleteIfExists(tempFilePath);
                } catch (IOException e) {
                    logger.error("临时文件清理失败", e);
                }
            }
        }

    }

    /**
     * 检查路径是否存在
     */
    private void validPath() {

        // 校验参数合法性
        try {
            logger.info("检查路径" + params.uid + " - " + params.savePath);
            nodeService.getPathNodeByPath(params.uid, params.savePath);
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("保存路径不存在" + params.savePath);
        }
    }

    /**
     * 初始化请求头，防止触发反爬
     */
    private void initHeader() {
        this.headers = new HttpHeaders();
        headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36");
        if (params.headers != null) {
            params.headers.forEach((key, val) -> {
                this.headers.add(key, val);
            });
        }
    }

    /**
     * 校验并初始化代理对象
     */
    private void initProxy() {
        if (params.proxy != null && params.proxy.length() != 0) {
            ProxyInfo proxy = proxyDao.getProxyByName(params.proxy);
            if (proxy == null) {
                throw new IllegalArgumentException("无效的代理：" + params.proxy);
            }
            sysProxy = proxy.toProxy();
            logger.debug("使用代理创建下载任务: " + proxy);
        }
    }

    /**
     * 初始化restTemplate
     */
    private void initRestTemplate() {
        IgnoreSSLHttpRequestFactory factory = new IgnoreSSLHttpRequestFactory();
        factory.setConnectTimeout(params.connectTimeout);
        factory.setReadTimeout(params.readTimeout);

        if (sysProxy != null) {
            factory.setProxy(sysProxy);
        }
        restTemplate = new RestTemplate(factory);
    }

    /**
     * 根据URL初步解析文件名
     */
    private void initFilename() {

        this.filename = StringUtils.getURLLastName(params.url);
        if (!StringUtils.hasText(filename) || this.filename.equals("/")) {
            this.filename = System.currentTimeMillis() + "";
            logger.warn("URL中不包含文件名信息, 临时设置为:" + this.filename);
        } else {
            logger.info("通过URL获取的默认文件名：" + filename);
        }
        progressRecord.setFilename(this.filename);
    }

    /**
     * 执行下载
     */
    private void startDownload() {
        restTemplate.execute(
                params.url,
                params.method,
                restTemplate.httpEntityCallback(new HttpEntity<>(headers)),
                response -> {
                    // 文件名探测
                    String serverFilename = response.getHeaders().getContentDisposition().getFilename();
                    if (serverFilename != null && FileNameValidator.valid(serverFilename)) {
                        logger.info("从响应头探测到文件名: " + serverFilename);
                        this.filename = serverFilename;
                    } else {
                        logger.info("响应头无文件名");
                    }

                    // 更新大小与文件名
                    long size = response.getHeaders().getContentLength();
                    progressRecord.setTotal(size);
                    downloadTaskRepo.updateSizeAndName(params.downloadId, size, this.filename);
                    logger.info("开始拉取文件, 文件大小: " + size + " 文件名: " + this.filename);

                    // 下载文件
                    this.tempFilePath = PathUtils.getAndCreateTempDirPath("offline-download")
                            .resolve(params.downloadId + "_" + this.filename);
                    logger.info("创建本地临时文件: " + this.tempFilePath.toAbsolutePath());
                    try (InputStream is = response.getBody(); OutputStream os = Files.newOutputStream(this.tempFilePath)) {

                        // 开始接收文件流
                        logger.info("文件接收中....");
                        this.receiverInputStream = is;
                        long s = 0;
                        this.progressRecord.setLoaded(0L);
                        while ((s = StreamUtils.copyRange(is, os, 0, 4095)) > 0) {
                            this.progressRecord.appendLoaded(s);
                        }

                        // 文件传输完整时记录已下载量为总量
                        if (!isCancel) {
                            this.progressRecord.setTotal(this.progressRecord.getLoaded());
                        }

                        // 更新大小和文件名
                        this.updateSizeAndName();

                        // 抛出异常以便让判定失败
                        if (isCancel) {
                            throw new RuntimeException("下载被中断");
                        }

                    } finally {
                        receiverInputStream = null;
                    }
                    logger.info("文件下载完成");
                    return null;
                }
        );
    }

    /**
     * 保存下载到的文件
     */
    private void saveFile() throws IOException {
        logger.info("准备保存文件，计算文件哈希散列值中...");

        // 获取文件信息（包括md5）
        FileInfo fileInfo = FileInfo.getLocal(tempFilePath.toString());

        logger.info("计算完成，md5为：" + fileInfo.getMd5());

        // 预判一波: 下载完成前，用户把保存目录删了，创建个目录防一手
        diskFileSystem.mkdirs(params.uid, params.savePath);
        logger.info("保存目录：" + params.savePath);


        // 处理文件重名的情况，后面加个".{重复次数}"
        int existCount = 0;
        String saveFilename = this.filename;
        String savePath = StringUtils.appendPath(params.savePath, saveFilename);
        while (diskFileSystem.exist(params.uid, savePath)) {
            logger.warn("目录存在同名文件，文件名调整为: " + saveFilename);
            existCount++;
            saveFilename = this.filename + "." + existCount;
            savePath = StringUtils.appendPath(params.savePath, saveFilename);
        }

        // 更改文件名为下载任务的文件名
        fileInfo.setName(saveFilename);

        logger.info("开始保存文件...");
        // 保存文件到网盘目录
        diskFileSystem.moveToSaveFile(params.uid, tempFilePath, params.savePath, fileInfo);
        logger.info("保存完成！");
    }

    private void updateSizeAndName() {
        downloadTaskRepo.updateSizeAndName(params.downloadId, progressRecord.getTotal(), filename);
    }

    @Override
    public void interrupt() {
        if (isCancel) {
            return;
        }
        isCancel = true;
        logger.info("收到下载中断指令");
        if (receiverInputStream != null) {
            try {
                receiverInputStream.close();
            } catch (IOException e) {
                logger.error("中断失败", e);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return isStart && !isFinish;
    }

    @Override
    public String getParams() {
        return originParams;
    }

    @Override
    public ProgressRecord getProgress() {
        return progressRecord;
    }
}
