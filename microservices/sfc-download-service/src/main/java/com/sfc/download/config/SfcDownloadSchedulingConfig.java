package com.sfc.download.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 下载服务的定时任务装配。
 *
 * <p>本服务需要周期执行的后台任务是「做种保留清理」（{@link com.sfc.download.seed.SeedTaskKeeper}）：
 * 扫描 {@code download_seed_task} 中已到期的做种记录，停止 aria2 做种并删除本地工作目录。</p>
 *
 * <p><b>为什么不加在启动类上：</b>启动类负责的是服务级能力装配（注册发现、内部鉴权、JPA 扫描等），
 * 定时任务是某一业务能力的实现细节；单独一个配置类可以把「为什么开启调度」「谁在用调度」写在一起，
 * 后续新增定时任务时也有明确的挂载点。</p>
 *
 * <p><b>间隔配置：</b>由各定时任务自身的 {@code @Scheduled} 表达式控制，做种清理的间隔为
 * {@code sfc.download.seed.check-interval-millis}（默认 60000 毫秒）。Spring 默认使用单线程调度器，
 * 因此所有定时任务串行执行；后续若新增耗时较长的定时任务，应同时配置专用调度线程池。</p>
 */
@Configuration
@EnableScheduling
public class SfcDownloadSchedulingConfig {
}
