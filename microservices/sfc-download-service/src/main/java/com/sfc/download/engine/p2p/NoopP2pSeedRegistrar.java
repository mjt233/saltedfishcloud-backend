package com.sfc.download.engine.p2p;

import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.model.DownloadTaskParams;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * {@link P2pSeedRegistrar} 的 no-op 兜底实现。
 *
 * <p>下载接管阶段通过 {@code ObjectProvider} 懒加载做种 SPI；当容器中没有做种生命周期实现时使用本实现，
 * 保证 P2 代码可独立编译与运行（此时 P2P 任务下载完成后不做种、目录按「完成即清理」收口）。</p>
 */
final class NoopP2pSeedRegistrar implements P2pSeedRegistrar {

    /**
     * 全局共享的无状态实例。
     */
    static final NoopP2pSeedRegistrar INSTANCE = new NoopP2pSeedRegistrar();

    /**
     * 工具类禁止外部实例化。
     */
    private NoopP2pSeedRegistrar() {
    }

    /**
     * {@inheritDoc}
     *
     * @return 恒定返回 {@code false}（不做种保留）
     */
    @Override
    public boolean register(DownloadContext context, P2pTaskState state, List<DownloadArtifact> artifacts) {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return 恒定返回 {@link Optional#empty()}（没有可复用的做种文件）
     */
    @Override
    public Optional<P2pSeedReuseTarget> findReusable(DownloadTaskParams params) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @return 恒定返回 {@code false}（没有目录处于做种保护中）
     */
    @Override
    public boolean isProtectedDirectory(Path dir) {
        return false;
    }
}
