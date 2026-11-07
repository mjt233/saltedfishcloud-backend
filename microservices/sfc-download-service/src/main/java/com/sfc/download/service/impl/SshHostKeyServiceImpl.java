package com.sfc.download.service.impl;

import com.sfc.cloud.data.service.SfcCrudServiceImpl;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.model.po.SshHostKey;
import com.sfc.download.repo.SshHostKeyRepo;
import com.sfc.download.service.SshHostKeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link SshHostKeyService} 的默认实现。
 *
 * <p>写库语义：{@link #trust(String, String, String, String, Long)} 是「尽力而为」的幂等写入——
 * 已存在同主机同指纹时直接返回，并发写入冲突只记 WARN。原因是主机密钥校验的结论在调用它之前
 * 已经判定完成，记录信任失败不应让本次克隆失败（否则首次克隆会因为一次数据库竞争而莫名失败）。</p>
 *
 * @see SshHostKeyService
 */
@Slf4j
@Service
public class SshHostKeyServiceImpl extends SfcCrudServiceImpl<SshHostKey, SshHostKeyRepo>
        implements SshHostKeyService {

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SshHostKey> listAll() {
        return repository.findAllByOrderByIdDesc();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SshHostKey> listByConnectAddress(String connectAddress) {
        if (!SfcStringUtils.hasText(connectAddress)) {
            return List.of();
        }
        return repository.findByConnectAddressOrderByIdDesc(connectAddress.trim());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean trust(String connectAddress, String keyType, String publicKey, String fingerprint, Long uid) {
        if (!SfcStringUtils.hasText(connectAddress) || !SfcStringUtils.hasText(fingerprint)) {
            log.warn("[SSH主机密钥] 信任记录缺少主机标识或指纹，拒绝记录: host={}", connectAddress);
            return false;
        }
        try {
            if (repository.existsByConnectAddressAndFingerprint(connectAddress, fingerprint)) {
                return true;
            }
            SshHostKey record = new SshHostKey();
            record.setConnectAddress(connectAddress);
            record.setKeyType(keyType);
            record.setPublicKey(publicKey);
            record.setFingerprint(fingerprint);
            record.setUid(uid);
            repository.save(record);
            log.info("[SSH主机密钥] 已记录首次信任: host={}, keyType={}, fingerprint={}, uid={}",
                    connectAddress, keyType, fingerprint, uid);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 并发场景下另一个任务可能刚好写入了同一记录（唯一约束冲突）——记录已存在，视为成功
            log.debug("[SSH主机密钥] 信任记录已被并发写入，视为成功: host={}, fingerprint={}",
                    connectAddress, fingerprint);
            return true;
        } catch (RuntimeException e) {
            // fail-closed：信任记录写不进去就不能放行，否则每次都是「首次信任」，TOFU 失效
            log.warn("[SSH主机密钥] 记录首次信任失败，将拒绝本次连接: host={}, cause={}",
                    connectAddress, e.toString());
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteRecord(Long id) {
        if (id == null || id <= 0) {
            throw new SfcJsonException(400, "SSH 主机信任记录id不能为空");
        }
        if (!repository.existsById(id)) {
            throw new SfcJsonException(404, "SSH 主机信任记录不存在: " + id);
        }
        repository.deleteById(id);
        log.info("[SSH主机密钥] 已删除信任记录: id={}", id);
    }
}
