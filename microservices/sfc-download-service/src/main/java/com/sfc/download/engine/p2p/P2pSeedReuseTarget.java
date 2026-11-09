package com.sfc.download.engine.p2p;

import java.util.Date;

/**
 * 做种复用目标：同 infohash（或同一种子内容）已在做种保留中，可直接复用其本地文件，无需重新下载。
 *
 * <p>由 {@link P2pSeedRegistrar#findReusable} 返回，供 P2P 任务协调器在提交 aria2 之前判断
 * 「能否直接读取现有文件并上传」。做种到期时间不因复用而延长。</p>
 *
 * @param seedTaskId  做种记录主键（{@code download_seed_task.id}）
 * @param downloadId  产生该做种记录的原下载任务主键
 * @param contentGid  真正产出内容的 aria2 gid，做种到期清理时使用
 * @param aria2Dir    做种时使用的 aria2 侧目录
 * @param localDir    本服务侧目录（复用方从这里读取文件上传）
 * @param seedEndAt   做种到期时间；到期后文件会被做种生命周期清理
 */
public record P2pSeedReuseTarget(Long seedTaskId,
                                 Long downloadId,
                                 String contentGid,
                                 String aria2Dir,
                                 String localDir,
                                 Date seedEndAt) {
}
