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
public class WrapServiceImpl implements WrapService {
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public String registerWrap(Integer uid, FileTransferInfo files) {
        String wid = SecureUtils.getUUID();
        redisTemplate.opsForValue().set(
                RedisKeyGenerator.getWrapKey(wid),
                new WrapInfo(uid, files),
                Duration.ofMinutes(30));
        return wid;
    }

    @Override
    public WrapInfo getWrapInfo(String wid) {
        return (WrapInfo)redisTemplate.opsForValue().get(RedisKeyGenerator.getWrapKey(wid));
    }
}
