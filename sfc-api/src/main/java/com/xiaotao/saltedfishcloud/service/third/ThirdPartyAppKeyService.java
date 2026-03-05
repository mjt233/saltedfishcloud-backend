package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppKey;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppKeyVo;
import com.xiaotao.saltedfishcloud.service.CrudService;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;

public interface ThirdPartyAppKeyService extends CrudService<ThirdPartyAppKey> {
    List<ThirdPartyAppKeyVo> listKeyByAppId(Long appId);

    /**
     * 删除整个第三方OAuth应用下的所有密钥
     */
    void deleteByAppId(Collection<Long> appIds);

    /**
     * 为应用生成新的密钥
     * @param appId 应用id
     * @param name 密钥名称
     * @return      新生成的密钥信息，其中clientSecret是原文
     */
    ThirdPartyAppKeyVo generateNewKey(Long appId,@Nullable String name);

    /**
     * 验证客户端密钥是否有效
     * @param appId         应用id
     * @param clientSecret  客户端密钥
     */
    boolean validate(Long appId, String clientSecret);

    /**
     * 修改密钥信息，仅能修改名称、备注等字段
     */
    void changeKeyInfo(ThirdPartyAppKeyVo thirdPartyAppKeyVo);
}