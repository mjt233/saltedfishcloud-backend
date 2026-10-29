package com.sfc.download.service;

import com.sfc.cloud.data.service.SfcCrudService;
import com.sfc.download.model.po.SshCredential;

import java.util.List;

/**
 * 用户 SSH 凭据服务。
 *
 * <p>在共享基座 {@link SfcCrudService} 的通用 CRUD 之外，补充三件业务语义：</p>
 * <ul>
 *   <li><b>归属校验</b>：{@link #findOwned(Long, long)} 同时校验「存在」与「属于当前用户」，
 *       对外统一返回「无效的 SSH 凭据」，不泄露他人凭据是否存在；</li>
 *   <li><b>创建校验</b>：{@link #create(SshCredential, long)} 在落库前校验认证方式与字段组合，
 *       私钥方式还会解析私钥并计算指纹，保证「能存进去的凭据一定可用」；</li>
 *   <li><b>执行期读取</b>：{@link #findForExecution(Long)} 供任务执行线程按 id 取凭据
 *       （此时没有 HTTP 请求上下文，不做归属校验——归属已在创建任务时校验过）。</li>
 * </ul>
 *
 * @see SshCredential
 */
public interface SshCredentialService extends SfcCrudService<SshCredential> {

    /**
     * 查询指定用户的全部 SSH 凭据（按创建时间倒序）。
     *
     * @param uid 用户 uid
     * @return 凭据列表，可能为空列表
     */
    List<SshCredential> listByUid(long uid);

    /**
     * 按 id 查询并校验凭据归属。
     *
     * @param id  凭据 id
     * @param uid 当前用户 uid
     * @return 命中且属于该用户的凭据
     * @throws com.sfc.common.exception.SfcJsonException 凭据不存在或不属于该用户时抛出（HTTP 400 语义）
     */
    SshCredential findOwned(Long id, long uid);

    /**
     * 按 id 读取凭据（执行期使用，不做归属校验）。
     *
     * @param id 凭据 id，允许为 {@code null}
     * @return 凭据；不存在或 id 为空时返回 {@code null}
     */
    SshCredential findForExecution(Long id);

    /**
     * 新增凭据。
     *
     * <p>会强制覆盖 {@code id}（新增语义）与 {@code uid}（归属当前用户），并在落库前完成
     * 认证方式、必填字段、私钥可解析性与指纹计算等校验。</p>
     *
     * @param entity 待保存的凭据
     * @param uid    凭据所有者 uid
     * @return 保存后的凭据（含自增主键与指纹）
     * @throws com.sfc.common.exception.SfcJsonException 参数非法或私钥无法解析时抛出（HTTP 400 语义）
     */
    SshCredential create(SshCredential entity, long uid);

    /**
     * 删除属于当前用户的凭据。
     *
     * @param id  凭据 id
     * @param uid 当前用户 uid
     * @throws com.sfc.common.exception.SfcJsonException 凭据不存在或不属于该用户时抛出（HTTP 400 语义）
     */
    void deleteOwned(Long id, long uid);
}
