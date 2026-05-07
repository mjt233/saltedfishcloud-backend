package com.xiaotao.saltedfishcloud.model.vo;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyAppUserAuthorizationVo {
    private ThirdPartyApp thirdPartyApp;

    private ThirdPartyAppAuthorization authorization;
}
