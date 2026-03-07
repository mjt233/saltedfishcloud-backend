package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppTokenPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.service.CrudService;

public interface ThirdPartyAppAuthorizationService extends CrudService<ThirdPartyAppAuthorization> {
    /**
     * 获取系统用户在第三方OAuth应用的授权信息
     * @param appId 第三方OAuth应用app
     * @param uid   系统用户id
     */
    ThirdPartyAppUserAuthorizationVo getUserAppAuthorization(Long appId, Long uid);

    /**
     * 为第三方OAuth应用授权权限
     * @param appId 要授权的第三方OAuth应用app
     * @param uid 系统用户id
     * @param scope 授权的权限范围，多个权限使用空格分割。该值为追加授权范围，可不必传入该用户已授权的权限。
     * @return 一次性生效授权码，第三方OAuth应用需要通过该授权码获取Access Token
     */
    String authorize(Long appId, Long uid, String scope);

    /**
     * 根据授权码获取开放接口的访问token，随后授权码将失效
     * @param code 授权码
     * @return  接口访问token
     */
    String getAccessToken(String code);

    /**
     * 解析并验证token是否有效
     */
    ThirdPartyAppTokenPayload parseAndValidateToken(String token);
}