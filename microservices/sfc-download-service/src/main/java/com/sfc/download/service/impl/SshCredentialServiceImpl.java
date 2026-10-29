package com.sfc.download.service.impl;

import com.sfc.cloud.data.service.SfcCrudServiceImpl;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.model.po.SshCredential;
import com.sfc.download.model.po.SshCredentialAuthType;
import com.sfc.download.repo.SshCredentialRepo;
import com.sfc.download.service.SshCredentialService;
import com.sfc.download.util.SshKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.util.List;

/**
 * {@link SshCredentialService} 的默认实现。
 *
 * <p>保存时完成全部「提交前即可判定」的校验，保证库里的凭据一定可用：私钥方式会真正解析一次私钥
 * （带口令则用口令解密）并计算指纹；密码方式要求用户名与密码齐全。这样任务执行期不会因为
 * 「存进去的私钥根本解析不了」而失败。</p>
 *
 * <p><b>日志口径：</b>只记录凭据 id、名称、认证方式与指纹，<b>绝不记录</b>私钥、口令或密码。</p>
 *
 * @see SshCredentialService
 */
@Slf4j
@Service
public class SshCredentialServiceImpl extends SfcCrudServiceImpl<SshCredential, SshCredentialRepo>
        implements SshCredentialService {

    /**
     * 凭据名称最大长度。
     */
    private static final int MAX_NAME_LENGTH = 128;

    /**
     * SSH 用户名最大长度。
     */
    private static final int MAX_USERNAME_LENGTH = 128;

    /**
     * 私钥内容最大长度（字符）：常见 4096 位 RSA 私钥约 3KB，32KB 足够覆盖多密钥拼接等异常输入。
     */
    private static final int MAX_PRIVATE_KEY_LENGTH = 32 * 1024;

    /**
     * 口令 / 密码最大长度。
     */
    private static final int MAX_SECRET_LENGTH = 4 * 1024;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SshCredential> listByUid(long uid) {
        return repository.findAllByUidOrderByIdDesc(uid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SshCredential findOwned(Long id, long uid) {
        if (id == null || id <= 0) {
            throw new SfcJsonException(400, "无效的 SSH 凭据: " + id);
        }
        return repository.findByIdAndUid(id, uid)
                .orElseThrow(() -> new SfcJsonException(400, "无效的 SSH 凭据: " + id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SshCredential findForExecution(Long id) {
        if (id == null) {
            return null;
        }
        return repository.findById(id).orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SshCredential create(SshCredential entity, long uid) {
        validateAndNormalize(entity);
        // 新增语义：忽略客户端可能携带的 id，归属强制为当前用户
        entity.setId(null);
        entity.setUid(uid);
        repository.save(entity);
        log.info("[SSH凭据] 新增凭据: uid={}, id={}, name={}, authType={}, fingerprint={}",
                uid, entity.getId(), entity.getName(), entity.getAuthType(), entity.getFingerprint());
        return entity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteOwned(Long id, long uid) {
        SshCredential credential = findOwned(id, uid);
        repository.delete(credential);
        log.info("[SSH凭据] 删除凭据: uid={}, id={}, name={}", uid, credential.getId(), credential.getName());
    }

    /**
     * 校验并规范化待保存的凭据。
     *
     * <p>规范化包括：去除名称/用户名两端空白、按认证方式清空无关字段、私钥方式计算指纹。
     * <b>密码与私钥口令不做 trim</b>——二者都可能包含有意义的空格，trim 会导致保存的凭证与用户
     * 实际使用的凭证不一致。</p>
     *
     * @param entity 待保存的凭据
     * @throws SfcJsonException 参数非法或私钥无法解析时抛出（HTTP 400 语义）
     */
    private void validateAndNormalize(SshCredential entity) {
        if (entity == null) {
            throw new SfcJsonException(400, "SSH 凭据不能为空");
        }
        String name = trimToNull(entity.getName());
        if (name == null) {
            throw new SfcJsonException(400, "SSH 凭据名称不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new SfcJsonException(400, "SSH 凭据名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        entity.setName(name);

        SshCredentialAuthType authType = entity.getAuthType();
        if (authType == null) {
            throw new SfcJsonException(400, "SSH 认证方式不能为空");
        }
        String username = trimToNull(entity.getUsername());
        if (username != null && username.length() > MAX_USERNAME_LENGTH) {
            throw new SfcJsonException(400, "SSH 用户名不能超过 " + MAX_USERNAME_LENGTH + " 个字符");
        }
        entity.setUsername(username);

        switch (authType) {
            case PRIVATE_KEY -> normalizePrivateKey(entity);
            case PASSWORD -> normalizePassword(entity);
            default -> throw new SfcJsonException(400, "不支持的 SSH 认证方式: " + authType);
        }
    }

    /**
     * 规范化私钥方式的凭据：校验私钥可解析并计算指纹，清空密码字段。
     *
     * @param entity 待保存的凭据
     * @throws SfcJsonException 私钥为空、过长或无法解析时抛出
     */
    private void normalizePrivateKey(SshCredential entity) {
        entity.setPassword(null);
        String privateKey = entity.getPrivateKey();
        if (!SfcStringUtils.hasText(privateKey)) {
            throw new SfcJsonException(400, "SSH 私钥不能为空");
        }
        if (privateKey.length() > MAX_PRIVATE_KEY_LENGTH) {
            throw new SfcJsonException(400, "SSH 私钥内容过长，最大支持 " + MAX_PRIVATE_KEY_LENGTH + " 个字符");
        }
        String passphrase = entity.getPassphrase();
        if (passphrase != null && passphrase.isEmpty()) {
            passphrase = null;
        }
        if (passphrase != null && passphrase.length() > MAX_SECRET_LENGTH) {
            throw new SfcJsonException(400, "SSH 私钥口令过长，最大支持 " + MAX_SECRET_LENGTH + " 个字符");
        }
        entity.setPassphrase(passphrase);
        List<KeyPair> keys = SshKeyUtils.loadKeyPairs(privateKey, passphrase);
        entity.setFingerprint(SshKeyUtils.fingerprint(keys.getFirst().getPublic()));
    }

    /**
     * 规范化密码方式的凭据：要求用户名与密码齐全，清空私钥相关字段。
     *
     * @param entity 待保存的凭据
     * @throws SfcJsonException 用户名或密码缺失、过长时抛出
     */
    private void normalizePassword(SshCredential entity) {
        entity.setPrivateKey(null);
        entity.setPassphrase(null);
        entity.setFingerprint(null);
        if (!SfcStringUtils.hasText(entity.getUsername())) {
            throw new SfcJsonException(400, "SSH 密码认证需要提供用户名");
        }
        String password = entity.getPassword();
        if (!SfcStringUtils.hasText(password)) {
            throw new SfcJsonException(400, "SSH 密码不能为空");
        }
        if (password.length() > MAX_SECRET_LENGTH) {
            throw new SfcJsonException(400, "SSH 密码过长，最大支持 " + MAX_SECRET_LENGTH + " 个字符");
        }
    }

    /**
     * 去空白；空白字符串返回 {@code null}。
     *
     * @param value 原始值，允许为 {@code null}
     * @return 去空白后的值，或 {@code null}
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
