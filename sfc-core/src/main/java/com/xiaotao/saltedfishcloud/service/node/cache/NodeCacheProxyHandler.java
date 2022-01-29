package com.xiaotao.saltedfishcloud.service.node.cache;

import com.xiaotao.saltedfishcloud.service.node.cache.annotation.RemoveNodeCache;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
public class NodeCacheProxyHandler {
    private final NodeCacheService cacheService;

    @Around("@annotation(anno)")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object handlePathToNodeId(ProceedingJoinPoint joinPoint, RemoveNodeCache anno) throws Throwable {
        Object[] args = joinPoint.getArgs();
        int uid = (int)args[anno.uid()];
        List<String> nids = new ArrayList<>();
        Object nid = args[anno.nid()];
        if (nid instanceof Collection) {
            nids.addAll((Collection)nid);
        } else {
            nids.add((String)nid);
        }

        cacheService.deleteNodeCache(uid, nids);

        return joinPoint.proceed();
    }

}
