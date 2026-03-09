package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
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
     * @return 授权后的用户授权对象信息
     */
    ThirdPartyAppAuthorization authorize(Long appId, Long uid, String scope);

    /**
     * 撤销权限授权
     * @param appId 要授权的第三方OAuth应用app
     * @param uid 系统用户id
     */
    void revoke(Long appId, Long uid);
}