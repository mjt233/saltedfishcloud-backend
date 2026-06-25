package com.sfc.dm.task.detect.scanner;

import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.enums.InvalidDataType;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 失效数据扫描器抽象基类
 */
@Slf4j
public abstract class AbstractInvalidDataScanner implements InvalidDataScanner {
    @Setter
    protected StoreServiceFactory storeServiceFactory;
    @Setter
    protected SysProperties sysProperties;
    @Setter
    protected UserService userService;
    @Setter
    protected SysCommonConfig sysCommonConfig;
    @Setter
    protected PrintWriter logWriter;
    @Setter
    protected AtomicBoolean interrupted;

    protected void log(String message) {
        if (logWriter != null) {
            logWriter.println(message);
            logWriter.flush();
        }
    }

    /**
     * 遍历所有用户
     *
     * @param action 对每个用户执行的操作
     */
    protected void forEachUser(Consumer<User> action) {
        int page = 0;
        int pageSize = 100;
        while (true) {
            if (interrupted.get()) {
                break;
            }
            PageableRequest pageableRequest = new PageableRequest();
            pageableRequest.setPage(page);
            pageableRequest.setSize(pageSize);
            CommonPageInfo<User> userPage = userService.listUsers(pageableRequest);
            if (userPage.getContent() == null || userPage.getContent().isEmpty()) {
                break;
            }
            for (User user : userPage.getContent()) {
                if (interrupted.get()) {
                    break;
                }
                action.accept(user);
            }
            if (page >= userPage.getTotalPage() - 1) {
                break;
            }
            page++;
        }
    }

    /**
     * 创建失效物理存储记录
     *
     * @param storagePath  物理存储路径
     * @param ownerUid     所属用户ID
     * @param diskPath     网盘路径
     * @param fileSize     文件大小
     * @param lastModified 最后修改时间
     * @param md5          文件MD5
     * @return 失效数据记录
     */
    protected InvalidDataRecord createInvalidPhysicalStorageRecord(String storagePath, Long ownerUid,
                                                    String diskPath, Long fileSize,
                                                    Date lastModified, String md5) {
        InvalidDataRecord record = new InvalidDataRecord();
        record.setType(InvalidDataType.PHYSICAL_STORAGE);
        record.setStoragePath(storagePath);
        record.setOwnerUid(ownerUid);
        record.setDiskPath(diskPath);
        record.setFileSize(fileSize);
        record.setLastModified(lastModified);
        record.setNeedIdentify(false);
        record.setStatus(InvalidDataStatus.PENDING);
        record.setMd5(md5);
        record.setStoreMode(sysCommonConfig.getStoreMode());
        return record;
    }

    /**
     * 创建失效文件记录
     *
     * @param storagePath  预期的物理存储路径
     * @param ownerUid     所属用户ID
     * @param diskPath     网盘路径
     * @param fileSize     文件大小
     * @param lastModified 最后修改时间
     * @param md5          文件MD5
     * @return 失效数据记录
     */
    protected InvalidDataRecord createInvalidFileRecordRecord(String storagePath, Long ownerUid,
                                                              String diskPath, Long fileSize,
                                                              Date lastModified, String md5) {
        InvalidDataRecord record = new InvalidDataRecord();
        record.setType(InvalidDataType.FILE_RECORD);
        record.setStoragePath(storagePath);
        record.setOwnerUid(ownerUid);
        record.setDiskPath(diskPath);
        record.setFileSize(fileSize);
        record.setLastModified(lastModified);
        record.setNeedIdentify(false);
        record.setStatus(InvalidDataStatus.PENDING);
        record.setMd5(md5);
        record.setStoreMode(sysCommonConfig.getStoreMode());
        return record;
    }
}
