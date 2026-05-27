package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppToken;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppAccessTokenPayload;
import com.xiaotao.saltedfishcloud.service.CrudService;

public interface ThirdPartyAppTokenService extends CrudService<ThirdPartyAppToken> {

    /**
     * 为第三方OAuth应用授权权限
     * @param appId 要授权的第三方OAuth应用app
     * @param uid 系统用户id
     * @param scope 授权的权限范围，多个权限使用空格分割。该值为追加授权范围，可不必传入该用户已授权的权限。
     * @return 授权成功后，返回授权码，第三方应用需要根据授权码获取Access Token
     * @see #getAccessToken(String, String)
     */
    String authorize(Long appId, Long uid, String scope);

    /**
     * 根据授权码获取开放接口的Access Token（永久Refresh Token），随后授权码将失效
     * @param code 授权码
     * @param clientSecret 第三方OAuth应用的客户端密钥
     * @return  接口访问授权Access Token
     */
    String getAccessToken(String code, String clientSecret);

    /**
     * 根据Access Token交换API访问凭证，最终访问API接口时使用该凭证。<br>
     * 当 {@code permanent=false} 时，签发临时ApiTicket，超时后需要重新调用该方法获取新凭证；<br>
     * 当 {@code permanent=true} 时，仅允许已开启能力的应用申请永久有效的ApiTicket。
     *
     * @param accessToken 接口访问授权Access Token
     * @param permanent   是否申请永久有效的ApiTicket
     * @param revokeOlder 是否自动撤销该用户id在该OAuth应用中旧ApiTicket的有效性
     * @return 开放平台接口访问凭证 ApiTicket
     */
    String getApiTicket(String accessToken, boolean permanent, boolean revokeOlder);


    /**
     * 撤销授权
     * @param appId 第三方应用id
     * @param uid   用户id
     */
    void revoke(Long appId, Long uid);

    /**
     * 为指定的第三方应用与用户签发遗留格式的 Access Token（长期有效，无过期时间）。
     * <p>
     * 该方法用于 OIDC refresh_token 的签发路径：将遗留 Access Token 作为 OIDC refresh_token 使用。
     * 签发的 token 以 SHA-256 指纹的 BCrypt 哈希持久化，以保证数据库安全。
     * </p>
     *
     * @param appId 第三方 OAuth 应用 ID
     * @param uid   系统用户 ID
     * @return 遗留格式的 Access Token 字符串（JWT）
     */
    String issueLegacyAccessToken(Long appId, Long uid);

    /**
     * 验证遗留格式的 Access Token 并返回其中的载荷。
     * <p>
     * 该方法除了解析 token 中的载荷外，还会校验数据库中的持久化指纹记录，
     * 以确保被撤销或失效的遗留 Access Token 不能继续作为 OIDC refresh_token 使用。
     * </p>
     *
     * @param accessToken 遗留格式的 Access Token 字符串（JWT）
     * @return 解析出的 {@link ThirdPartyAppAccessTokenPayload} 载荷对象
     * @throws com.xiaotao.saltedfishcloud.exception.JsonException 当 token 格式无效时抛出
     */
    ThirdPartyAppAccessTokenPayload validateLegacyAccessToken(String accessToken);
}
