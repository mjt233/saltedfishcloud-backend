package com.sfc.quickshare.service;

import com.sfc.quickshare.model.QuickShare;
import com.sfc.quickshare.model.QuickShareProperty;
import com.sfc.quickshare.repo.QuickShareRepo;
import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.constant.ByteSize;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorage;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageDomainDefinition;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageManager;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class QuickShareService {
    private final static String LOG_PREFIX = "[快速分享]";

    @Autowired
    @Getter
    private QuickShareRepo repo;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private QuickShareProperty property;

    /**
     * 快速分享文件附属存储。
     */
    private AttachStorage quickShareStorage;

    /**
     * 注册快速分享附属存储域。
     *
     * @param attachStorageManager 附属存储管理器
     */
    @Autowired
    public void setAttachStorageManager(AttachStorageManager attachStorageManager) {
        attachStorageManager.registerStorageDomain(AttachStorageDomainDefinition.builder()
                .id("quick_share")
                .name("快速分享")
                .description("快速分享临时文件")
                .build());
        quickShareStorage = attachStorageManager.getStorage("quick_share");
    }

    private String getCacheKey(String code) {
        return CacheKeyPrefixes.QUICK_SHARE + code;
    }

    /**
     * 录入提取码
     * @param code  提取码
     * @param id    快速分享id
     * @return      实际录入的提取码
     */
    private String saveCode(String code, Long id) {
        String actualCode = code.toLowerCase();
        long effectiveDuration = property.getEffectiveDuration();
        while (!cacheService.setIfAbsent(this.getCacheKey(actualCode), id, effectiveDuration, TimeUnit.MINUTES)) {
            actualCode = StringUtils.getRandomString(5, false);
        }
        return actualCode;
    }


    /**
     * 保存临时文件
     * @param resource      待保持文件资源
     * @param quickShare    快速分享对象
     * @return 该文件的文件提取码
     * @throws IOException  IO异常
     */
    @Transactional(rollbackFor = Exception.class)
    public String saveTempFile(Resource resource, QuickShare quickShare) throws IOException {
        this.checkIsEnable();
        long fileSize = resource.contentLength();
        this.checkSize(fileSize);

        // 设置基础数据
        quickShare.setId(null);
        quickShare.setFileName(resource.getFilename());
        quickShare.setSize(fileSize);

        // 设置基础数据 - 标记过期日期
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, property.getEffectiveDuration());
        quickShare.setExpiredAt(calendar.getTime());

        // 先持久化新实体，确保由Hibernate生成主键，避免save时因非空id走merge逻辑
        // 导致新记录被误判为更新，从而触发ObjectOptimisticLockingFailureException。
        repo.saveAndFlush(quickShare);

        // 存储文件
        quickShareStorage.saveFile(this.getFilePath(quickShare.getId()), resource);

        // 生成并记录提取码
        String actualCode = this.saveCode(quickShare.getCode(), quickShare.getId());
        quickShare.setCode(actualCode);

        // 刷新最终提取码
        repo.saveAndFlush(quickShare);

        return actualCode;
    }


    /**
     * 根据文件提取码获取文件快速分享id
     * @param code  文件提取码
     * @return      如果提取码不存在或失效，则为null，
     */
    private Long getIdByCode(String code) {
        Object id = cacheService.get(getCacheKey(code));
        if (id == null) {
            return null;
        } else {
            return TypeUtils.toLong(id);
        }
    }

    /**
     * 获取快速分享文件的在临时存储服务上的存储路径
     * @param id    分享id
     */
    private String getFilePath(Long id) {
        return id.toString();
    }

    /**
     * 获取快速分享文件的文件存储服务
     */
    private AttachStorage getStoreService() {
        return quickShareStorage;
    }

    /**
     * 在存储上移除快速分享文件
     * @param id    快速分享id
     */
    public void deleteFile(Long id) throws IOException {
        this.getStoreService().delete(this.getFilePath(id));
    }

    /**
     * 清理所有过期文件
     */
    public void cleanExpiredFiles() throws IOException {
        // 筛选出过期的快速分享
        List<QuickShare> allExpired = repo.findAllExpired(new Date());
        if (allExpired != null && !allExpired.isEmpty()) {
            List<QuickShare> successList = new ArrayList<>(allExpired.size());

            // 在存储服务上删除文件
            for (QuickShare quickShare : allExpired) {
                try {
                    deleteFile(quickShare.getId());
                    log.info("{}清理过期文件：{}", LOG_PREFIX, quickShare.getId());
                    successList.add(quickShare);
                } catch (Throwable e) {
                    log.error("{}过期文件{}清理失败", LOG_PREFIX, quickShare.getId(), e);
                }
            }

            // 从数据库中移除删除的记录
            if (!successList.isEmpty()) {
                repo.deleteAllInBatch(successList);
            }

        }
    }

    /**
     * 根据分享id获取快速分享的文件
     * @param id    分享id
     * @return      文件资源
     */
    public Resource getFileById(Long id) throws IOException {
        this.checkIsEnable();
        Resource resource = this.getStoreService().getFile(this.getFilePath(id)).orElse(null);
        if (resource == null || !resource.exists()) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
        return resource;
    }

    private void checkIsEnable() {
        if (!Boolean.TRUE.equals(property.getIsEnabled())) {
            throw new IllegalArgumentException("功能未开启");
        }
    }

    private void checkSize(long fileSize) {
        long maxSize = property.getMaxSize() * ByteSize._1MiB;
        if (fileSize > maxSize) {
            throw new IllegalArgumentException("文件不能大于" + StringUtils.getFormatSize(maxSize));
        }
    }

    /**
     * 根据提取码获取分享信息
     * @param code  提取码
     * @return      分享信息，若不存在则抛出异常
     */
    public QuickShare getByCode(String code) {
        this.checkIsEnable();
        Long id = getIdByCode(code);
        if (id == null) {
            throw new JsonException("提取码不存在或文件已过期");
        }
        return repo.findById(id).orElseThrow(() -> new JsonException("文件已过期或记录失效"));
    }


}
