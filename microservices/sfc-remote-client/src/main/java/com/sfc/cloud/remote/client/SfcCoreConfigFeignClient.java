package com.sfc.cloud.remote.client;

import com.sfc.cloud.constants.SfcServiceName;
import com.sfc.cloud.model.SfcConfigItem;
import com.sfc.common.model.json.SfcJsonResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * sfc-core 系统配置客户端
 */
@FeignClient(
        name = SfcServiceName.CORE,
        contextId = "sfcCoreConfigClient",
        path = SfcCoreConfigFeignClient.PREFIX
)
public interface SfcCoreConfigFeignClient {

    /** 接口前缀 */
    String PREFIX = "/api/internal/config";

    /**
     * 获取单个配置项的值。
     *
     * @param key 配置项名称，不能为空
     * @return core 的响应信封，业务数据为配置项原始值（键不存在时为 {@code null}）
     */
    @GetMapping("get")
    SfcJsonResult<String> getConfig(@RequestParam("key") String key);

    /**
     * 批量获取配置项的值。
     *
     * @param keys 配置项名称列表，不能为空
     * @return core 的响应信封，业务数据为「配置项名称 → 原始值」的映射（不存在的键不会出现在结果中）
     */
    @GetMapping("batchGet")
    SfcJsonResult<Map<String, String>> batchGetConfig(@RequestParam("keys") List<String> keys);

    /**
     * 设置单个配置项的值（键不存在时新增）。
     *
     * @param config 配置项，名称不能为空
     * @return core 的响应信封（空成功）
     */
    @PostMapping("set")
    SfcJsonResult<Void> setConfig(@RequestBody SfcConfigItem config);

    /**
     * 批量设置配置项的值（键不存在时新增）。
     *
     * @param configs 配置项列表，不能为空，且每一项的名称不能为空
     * @return core 的响应信封（空成功）
     */
    @PostMapping("batchSet")
    SfcJsonResult<Void> batchSetConfig(@RequestBody List<SfcConfigItem> configs);
}
