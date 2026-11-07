package com.sfc.download.engine;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 任务临时目录创建策略（函数式接口）。
 *
 * <p>由任务处理器按下载类型选择并注入 {@link DownloadContext}，使「临时目录放在哪里」成为可替换的
 * 执行策略：</p>
 * <ul>
 *   <li>HTTP、Git 等在本服务进程内完成下载的类型使用默认策略，临时目录位于本服务 JVM 临时目录
 *       （{@code ${java.io.tmpdir}/offline-download}）；</li>
 *   <li>磁力链（MAGNET）与种子（TORRENT）使用 aria2 共享工作区策略：配置了跨容器共享目录时，
 *       任务目录落在共享目录下，使 aria2 容器写入的文件对本服务可见。</li>
 * </ul>
 *
 * <p>实现返回的目录必须已经创建（或已存在）；同一次执行内允许按需创建多个隔离目录。</p>
 *
 * @see DownloadContext#getTempDir()
 */
@FunctionalInterface
public interface DownloadTempDirCreator {

    /**
     * 为一次下载任务创建独立的临时目录。
     *
     * @param taskFlag 任务标识（通常为下载任务 id），允许为 {@code null} 或空白
     * @return 已创建的任务临时目录
     * @throws IOException 目录创建失败时抛出
     */
    Path create(String taskFlag) throws IOException;
}
