package com.sfc.download.stash;

import com.sfc.download.engine.DownloadArtifact;

import java.util.List;

/**
 * 下载产物落盘服务：把下载引擎产出的本地文件写入用户网盘。
 *
 * <p><b>为什么单独成服务：</b>下载引擎只负责把内容取到本地（HTTP 流、Git 工作区、aria2 下载目录），
 * 与「怎么写进网盘」解耦。新增下载类型时无需关心重名规避、目录创建与上传顺序。</p>
 *
 * <p><b>两种落盘形态：</b></p>
 * <ul>
 *   <li><b>单文件</b>：产物只有一个且位于任务临时目录根部时，直接以文件名落盘，重名按
 *       {@code .1}/{@code .2} 规则追加后缀；</li>
 *   <li><b>目录树</b>：多文件或带层级的产物，在保存目录下建一个顶层目录，再按相对路径逐级建目录、
 *       逐文件上传，完整保留引擎产出的目录结构（Git 仓库、多文件种子等）。</li>
 * </ul>
 *
 * <p><b>失败语义：</b>任一目录创建或文件上传失败立即抛出业务异常（由任务处理器记为任务失败）；
 * 已成功上传的文件<b>不做回滚</b>，避免误删用户网盘中的既有数据。</p>
 */
public interface DownloadSaveService {

    /**
     * 把产物写入网盘。
     *
     * @param request 落盘请求
     * @return 落盘结果（最终保存名与实际上传字节数）
     * @throws com.sfc.common.exception.SfcJsonException 产物为空、目录创建失败或上传失败时抛出
     */
    StashResult stash(StashRequest request);

    /**
     * 落盘请求。
     *
     * @param uid          资源归属用户 uid（{@code 0} 表示公共网盘，写入需要管理员权限）
     * @param savePath     保存到网盘的目标目录（不含文件名，根目录为 {@code "/"}）
     * @param topLevelName 目录树落盘时使用的顶层目录名；{@code null} 或空白时按产物公共根目录推断，
     *                     再退化为时间戳
     * @param artifacts    下载产物列表，{@code null} 按空列表处理
     */
    record StashRequest(long uid, String savePath, String topLevelName, List<DownloadArtifact> artifacts) {

        /**
         * 规范化产物列表：{@code null} 转为空列表，并做不可变拷贝，避免调用方后续修改影响落盘过程。
         */
        public StashRequest {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    /**
     * 落盘结果。
     *
     * @param savedName  最终保存名：单文件为文件名，目录树为顶层目录名（均已做过重名规避）
     * @param totalBytes 实际上传的字节数合计
     */
    record StashResult(String savedName, long totalBytes) {
    }
}
