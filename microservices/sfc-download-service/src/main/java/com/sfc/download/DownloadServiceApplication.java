package com.sfc.download;

import com.sfc.cloud.auth.annotation.EnableSfcInternalAuth;
import com.sfc.cloud.task.worker.annotation.EnableSfcTaskWorker;
import com.sfc.download.model.po.DownloadTaskInfo;
import com.sfc.download.repo.DownloadTaskRepo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 咸鱼云离线下载微服务启动类。
 *
 * <p><b>能力装配：</b></p>
 * <ul>
 *   <li>{@link EnableDiscoveryClient}：注册到服务注册中心（local 或 Nacos），供网关路由与
 *       core 的任务调度定位本实例；</li>
 *   <li>{@link EnableSfcInternalAuth}：开启 {@code /api/internal/**} 内部接口的出入站鉴权
 *       （共享密钥 {@code sfc.internal.key}），Worker 上报与入站中断指令都依赖它；</li>
 *   <li>{@link EnableSfcTaskWorker}：以 {@code SERVICE} 角色接入 core 的异步任务系统，
 *       认领并执行 {@code offline-download} 类型的任务；</li>
 *   <li>{@link EnableJpaAuditing}：启用 Spring Data JPA 审计，为继承
 *       {@link com.sfc.cloud.data.model.SfcAuditModel} 的实体填充创建/更新时间；</li>
 *   <li>{@link EntityScan} / {@link EnableJpaRepositories}：本服务拥有独立的 SQLite 数据库，
 *       实体与仓储只在本包内扫描，避免误扫到共享库中的其他实体。</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableSfcInternalAuth
@EnableSfcTaskWorker
@EnableJpaAuditing
@EntityScan(basePackageClasses = DownloadTaskInfo.class)
@EnableJpaRepositories(basePackageClasses = DownloadTaskRepo.class)
public class DownloadServiceApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    static void main(String[] args) {
        SpringApplication.run(DownloadServiceApplication.class, args);
    }
}
