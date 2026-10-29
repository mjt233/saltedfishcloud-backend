package com.sfc.download.controller;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.identity.SfcCurrentUserSupport;
import com.sfc.download.model.po.SshHostKey;
import com.sfc.download.model.vo.SshHostKeyVO;
import com.sfc.download.service.SshHostKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SSH 主机密钥信任记录管理接口（仅管理员）。
 *
 * <p>信任记录由 Git SSH 克隆在「首次连接未知主机」时自动写入（TOFU），本接口只提供人工维护能力：
 * 查询与删除。典型场景是主机密钥轮换后克隆失败，管理员删除该主机的旧记录，下一次克隆会重新信任。</p>
 *
 * <p>权限：未登录返回 401；已登录但非管理员返回 403（管理员判定经 core 回源，见
 * {@link SfcCurrentUserSupport#currentIsAdmin()}）。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(SshHostKeyController.PREFIX)
@Validated
public class SshHostKeyController {

    /**
     * 接口前缀。
     */
    public static final String PREFIX = "/api/task/download/ssh-host-key";

    /**
     * SSH 主机密钥信任记录服务。
     */
    private final SshHostKeyService sshHostKeyService;

    /**
     * 查询 SSH 主机密钥信任记录。
     *
     * @param connectAddress 可选的主机标识过滤条件（{@code host} 或 {@code host:port}）；为空时返回全部
     * @return 信任记录列表，可能为空列表
     */
    @GetMapping
    public SfcJsonResult<List<SshHostKeyVO>> list(@RequestParam(required = false) String connectAddress) {
        requireAdmin();
        List<SshHostKey> records = SfcStringUtils.hasText(connectAddress)
                ? sshHostKeyService.listByConnectAddress(connectAddress.trim())
                : sshHostKeyService.listAll();
        return SfcJsonResult.getInstance(records.stream().map(SshHostKeyVO::of).toList());
    }

    /**
     * 删除一条 SSH 主机密钥信任记录。
     *
     * <p>删除后，下一次连接该主机会重新执行「首次信任」。仅在确认主机密钥确实已变更时使用。</p>
     *
     * @param id 信任记录 id
     * @return 空成功响应
     */
    @DeleteMapping
    public SfcJsonResult<Object> delete(@RequestParam Long id) {
        requireAdmin();
        sshHostKeyService.deleteRecord(id);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 校验当前用户为已登录的管理员。
     *
     * @throws SfcJsonException 未登录抛出 401；非管理员抛出 403
     */
    private static void requireAdmin() {
        if (SfcCurrentUserSupport.getCurrentUid() == null) {
            throw new SfcJsonException(401, "请先登录");
        }
        if (!SfcCurrentUserSupport.currentIsAdmin()) {
            throw new SfcJsonException(403, "仅管理员可管理 SSH 主机信任记录");
        }
    }
}
