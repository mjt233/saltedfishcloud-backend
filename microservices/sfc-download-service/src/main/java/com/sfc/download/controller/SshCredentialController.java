package com.sfc.download.controller;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.download.identity.SfcCurrentUserSupport;
import com.sfc.download.model.param.SshCredentialCreateParam;
import com.sfc.download.model.po.SshCredential;
import com.sfc.download.model.vo.SshCredentialVO;
import com.sfc.download.service.SshCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户 SSH 凭据管理接口。
 *
 * <p>凭据按用户隔离：所有接口只操作「当前登录用户」自己的凭据，不接受客户端传入 uid；
 * 未登录返回信封 {@code code=401}。凭据内容（私钥/口令/密码）<b>只进不出</b>，
 * 响应中只返回 {@link SshCredentialVO} 的元信息。</p>
 *
 * <p>接口路径挂在离线下载前缀下（{@value #PREFIX}），经网关访问时为
 * {@code /api/service/sfc-download-service/task/download/ssh-credential}。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(SshCredentialController.PREFIX)
@Validated
public class SshCredentialController {

    /**
     * 接口前缀。
     */
    public static final String PREFIX = "/api/task/download/ssh-credential";

    /**
     * SSH 凭据服务。
     */
    private final SshCredentialService sshCredentialService;

    /**
     * 查询当前用户配置的全部 SSH 凭据。
     *
     * @return 凭据列表（不含私钥/口令/密码），可能为空列表
     */
    @GetMapping
    public SfcJsonResult<List<SshCredentialVO>> list() {
        long uid = requireLoginUid();
        List<SshCredentialVO> result = sshCredentialService.listByUid(uid).stream()
                .map(SshCredentialVO::of)
                .toList();
        return SfcJsonResult.getInstance(result);
    }

    /**
     * 新增一条 SSH 凭据。
     *
     * <p>私钥认证方式会在保存前解析一次私钥（带口令则用口令解密）并计算指纹；密码认证方式要求
     * 用户名与密码齐全。校验失败返回 400。</p>
     *
     * @param param 新增参数
     * @return 新增后的凭据元信息
     */
    @PostMapping
    public SfcJsonResult<SshCredentialVO> create(@RequestBody @Validated SshCredentialCreateParam param) {
        long uid = requireLoginUid();
        SshCredential credential = sshCredentialService.create(param.toEntity(), uid);
        return SfcJsonResult.getInstance(SshCredentialVO.of(credential));
    }

    /**
     * 删除当前用户的一条 SSH 凭据。
     *
     * @param id 凭据 id
     * @return 空成功响应
     */
    @DeleteMapping
    public SfcJsonResult<Object> delete(@RequestParam Long id) {
        long uid = requireLoginUid();
        sshCredentialService.deleteOwned(id, uid);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 获取当前登录用户 uid，未登录时抛出 401 业务异常。
     *
     * @return 当前用户 uid
     * @throws SfcJsonException 未登录时抛出（HTTP 401 语义）
     */
    private static long requireLoginUid() {
        Long uid = SfcCurrentUserSupport.getCurrentUid();
        if (uid == null) {
            throw new SfcJsonException(401, "请先登录");
        }
        return uid;
    }
}
