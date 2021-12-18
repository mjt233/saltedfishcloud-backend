package com.xiaotao.saltedfishcloud.service.wrap;

import com.xiaotao.saltedfishcloud.entity.FileTransferInfo;
import com.xiaotao.saltedfishcloud.helper.RedisKeyGenerator;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class WrapService {
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 注册一个打包信息
     * @param uid       资源所属用户ID
     * @param files     打包的资源信息
     * @return 打包ID
     */
    public String registerWrap(Integer uid, FileTransferInfo files) {
        String wid = SecureUtils.getUUID();
        redisTemplate.opsForValue().set(
                RedisKeyGenerator.getWrapKey(wid),
                new WrapInfo(uid, files),
                Duration.ofMinutes(30));
        return wid;
    }

    /**
     * 获取打包信息
     * @param wid   打包ID
     * @return  打包信息
     */
    public WrapInfo getWrapInfo(String wid) {
        return (WrapInfo)redisTemplate.opsForValue().get(RedisKeyGenerator.getWrapKey(wid));
    }
}
