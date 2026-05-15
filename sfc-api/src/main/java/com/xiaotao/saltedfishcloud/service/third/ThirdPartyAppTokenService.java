package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppToken;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
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
     * 根据Access Token获取临时API访问凭证，最终访问API接口时使用该凭证。
     *
     * @param appId       第三方OAuth应用id
     * @param uid         用户id
     * @param accessToken 接口访问授权Access Token
     * @return 开放平台接口访问凭证 ApiTicket
     */
    default String getApiTicket(Long appId, Long uid, String accessToken) {
        return getApiTicket(appId, uid, accessToken, false);
    }

    /**
     * 根据Access Token获取API访问凭证，最终访问API接口时使用该凭证。<br>
     * 当 {@code permanent=false} 时，签发临时ApiTicket，超时后需要重新调用该方法获取新凭证；<br>
     * 当 {@code permanent=true} 时，仅允许已开启能力的应用申请永久有效的ApiTicket。
     *
     * @param appId       第三方OAuth应用id
     * @param uid         用户id
     * @param accessToken 接口访问授权Access Token
     * @param permanent   是否申请永久有效的ApiTicket
     * @return 开放平台接口访问凭证 ApiTicket
     */
    String getApiTicket(Long appId, Long uid, String accessToken, boolean permanent);

    /**
     * 解析并验证Api Ticket是否有效
     */
    ThirdPartyAppApiTicketPayload parseAndValidateApiTicket(String apiTicket);

    /**
     * 撤销授权
     * @param appId 第三方应用id
     * @param uid   用户id
     */
    void revoke(Long appId, Long uid);
}
