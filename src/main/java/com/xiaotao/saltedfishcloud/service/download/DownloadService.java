package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.dao.jpa.DownloadTaskRepository;
import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.po.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContext;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

@Service
public class DownloadService {
    @Resource
    private DownloadTaskRepository downloadDao;
    @Resource
    private ProxyDao proxyDao;
    @Resource
    private TaskContextFactory factory;
    @Resource
    private NodeService nodeService;
    @Resource
    private FileService fileService;

    /**
     * 创建一个下载任务
     * @param params 任务参数
     * @return 下载任务ID
     */
    public String createTask(DownloadTaskParams params, int creator) throws NoSuchFileException {
        // 初始化下载任务和上下文
        var builder = DownloadTaskBuilder.create(params.url);
        builder.setHeaders(params.headers);
        builder.setMethod(params.method);
        if (params.proxy != null && params.proxy.length() != 0) {
            ProxyInfo proxy = proxyDao.getProxyByName(params.proxy);
            if (proxy == null) {
                throw new JsonException(400, "无效的代理：" + params.proxy);
            }
            builder.setProxy(proxy.toProxy());
        }

        // 校验参数合法性
        nodeService.getPathNodeByPath(params.uid, params.savePath);

        DownloadTask task = builder.build();
        TaskContext<DownloadTask> context = factory.createContextFromAsyncTask(task);
        // 初始化下载任务信息和录入数据库
        var info = new DownloadTaskInfo();
        info.id = context.getId();
        info.url = params.url;
        info.proxy = params.proxy;
        info.uid = params.uid;
        info.state = "downloading";
        info.createdBy = creator;
        info.savePath = params.savePath;
        downloadDao.save(info);

        // 绑定事件回调
        context.onSuccess(() -> {
            info.state = "finish";
            try {
                // 创建预期的保存目录以应对下载完成前用户删除目录的情况
                fileService.mkdirs(params.uid, params.savePath);

                // 获取文件信息（包括md5）
                var tempFile = Paths.get(task.getSavePath());
                var fileInfo = FileInfo.getLocal(tempFile.toString());

                // 更改文件名为下载任务的文件名
                if (task.getStatus().name != null) {
                    fileInfo.setName(task.getStatus().name);
                }

                // 保存文件到网盘目录
                fileService.moveToSaveFile(params.uid, tempFile, params.savePath, fileInfo);
            } catch (FileAlreadyExistsException e) {
                // 处理用户删除了目录且指定目录路径中存在同名文件的情况
                info.savePath = "/download" + System.currentTimeMillis() + info.savePath;
                try {
                    fileService.mkdirs(params.uid, info.savePath);
                } catch (FileAlreadyExistsException | NoSuchFileException ex) {
                    // 依旧失败那莫得办法咯
                    info.message = e.getMessage();
                    info.state = "failed";
                }
            } catch (Exception e) {
                // 文件保存失败
                e.printStackTrace();
                info.message = e.getMessage();
                info.state = "failed";
            }
            downloadDao.save(info);
        });
        context.onFailed(() -> {
            info.state = "failed";
            info.message = task.getStatus().error;
            downloadDao.save(info);
        });

        // 提交任务执行
        factory.getManager().submit(context);

        return context.getId();
    }
}
