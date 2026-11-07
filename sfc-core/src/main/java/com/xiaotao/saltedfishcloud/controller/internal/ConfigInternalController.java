package com.xiaotao.saltedfishcloud.controller.internal;

import com.sfc.cloud.model.SfcConfigItem;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcStringUtils;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 系统配置项内部控制器。仅用于微服务系统内部调用。
 */
@RestController
@RequestMapping(ConfigInternalController.PREFIX)
@RequiredArgsConstructor
@Tag(name = "系统配置-sfc-core微服务间内部接口")
public class ConfigInternalController {

    /** 接口前缀 */
    public static final String PREFIX = "/api/internal/config";

    /** 系统配置服务 */
    private final ConfigService configService;

    /**
     * 获取单个配置项的值。
     *
     * @param key 配置项名称
     * @return 配置项原始值；配置项不存在时为 {@code null}
     */
    @GetMapping("get")
    @AllowAnonymous
    public SfcJsonResult<String> getConfig(@RequestParam("key") String key) {
        requireKey(key);
        return SfcJsonResult.getInstance(configService.getConfig(key));
    }

    /**
     * 批量获取配置项的值。
     *
     * @param keys 配置项名称列表，不能为空
     * @return 键为配置项名称、值为配置项原始值的映射；不存在的配置项不会出现在结果中
     */
    @GetMapping("batchGet")
    @AllowAnonymous
    public SfcJsonResult<Map<String, String>> batchGetConfig(@RequestParam("keys") List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new SfcJsonException(400, "配置项名称列表不能为空");
        }
        for (String key : keys) {
            requireKey(key);
        }
        return SfcJsonResult.getInstance(configService.listConfig(keys));
    }

    /**
     * 设置单个配置项的值（配置项不存在时新增）。
     *
     * @param config 配置项，名称不能为空；值为 {@code null} 时由配置服务按空值写入
     * @return 空成功响应
     */
    @PostMapping("set")
    @AllowAnonymous
    public SfcJsonResult<Object> setConfig(@RequestBody SfcConfigItem config) {
        requireConfig(config);
        configService.setConfig(config.getName(), config.getValue());
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 批量设置配置项的值（配置项不存在时新增）。
     *
     * <p>同一批次内出现同名配置项时，以列表中靠后的值为准（由配置服务逐条覆盖写入）。</p>
     *
     * @param configs 配置项列表，不能为空，且每一项的名称不能为空
     * @return 空成功响应
     * @throws IOException 配置项值序列化/写入异常
     */
    @PostMapping("batchSet")
    @AllowAnonymous
    public SfcJsonResult<Object> batchSetConfig(@RequestBody List<SfcConfigItem> configs) throws IOException {
        if (configs == null || configs.isEmpty()) {
            throw new SfcJsonException(400, "配置项列表不能为空");
        }
        List<NameValueType<String>> configList = new ArrayList<>(configs.size());
        for (SfcConfigItem config : configs) {
            requireConfig(config);
            configList.add(new NameValueType<>(config.getName(), config.getValue()));
        }
        configService.batchSetConfig(configList);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 校验配置项名称非空。
     *
     * @param key 配置项名称
     */
    private void requireKey(String key) {
        if (!SfcStringUtils.hasText(key)) {
            throw new SfcJsonException(400, "配置项名称不能为空");
        }
    }

    /**
     * 校验配置项本身及其名称非空。
     *
     * @param config 配置项
     */
    private void requireConfig(SfcConfigItem config) {
        if (config == null) {
            throw new SfcJsonException(400, "配置项不能为空");
        }
        requireKey(config.getName());
    }
}
