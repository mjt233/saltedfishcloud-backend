package com.xiaotao.saltedfishcloud.service.comment;

import com.github.houbb.sensitive.word.api.IWordDeny;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.deny.WordDenys;
import com.xiaotao.saltedfishcloud.model.config.CommentSafeConfig;
import com.xiaotao.saltedfishcloud.model.config.SysSafeConfig;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * 评论敏感词检测器提供者，以 Spring Bean 形式管理 SensitiveWordBs 的生命周期，
 * 并在配置变更时自动同步重建。
 */
@Component
public class CommentSensitiveWordProvider {

    private volatile SensitiveWordBs sensitiveWordBs;

    private final ConfigService configService;
    private final SysSafeConfig sysSafeConfig;

    public CommentSensitiveWordProvider(ConfigService configService, SysSafeConfig sysSafeConfig) {
        this.configService = configService;
        this.sysSafeConfig = sysSafeConfig;
    }

    /**
     * 初始化并监听配置变更
     */
    @PostConstruct
    public void init() {
        rebuild();
        String configKey = PropertyUtils.parseLambdaConfigName(SysSafeConfig::getCommentSafeConfig);
        configService.addAfterSetListener(configKey, newVal -> rebuild());
    }

    /**
     * 检测文本是否包含敏感词，仅在开启内容安全过滤时生效
     *
     * @param text 待检测文本
     * @return 包含敏感词返回 true，否则返回 false
     */
    public boolean contains(String text) {
        SensitiveWordBs current = this.sensitiveWordBs;
        return current != null && current.contains(text);
    }

    /**
     * 根据当前配置重建敏感词检测器
     */
    private synchronized void rebuild() {
        CommentSafeConfig config = sysSafeConfig.getCommentSafeConfig();
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            this.sensitiveWordBs = buildSensitiveWordBs(config);
        } else {
            this.sensitiveWordBs = null;
        }
    }

    /**
     * 根据配置构建 SensitiveWordBs 实例
     */
    private static SensitiveWordBs buildSensitiveWordBs(CommentSafeConfig config) {
        IWordDeny customDeny = () -> {
            String customWords = config.getCustomSensitiveWords();
            if (!StringUtils.hasText(customWords)) {
                return Collections.emptyList();
            }
            return Arrays.stream(customWords.split("\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        };

        return SensitiveWordBs.newInstance()
                .wordDeny(WordDenys.chains(WordDenys.defaults(), customDeny))
                .ignoreCase(Boolean.TRUE.equals(config.getIgnoreCase()))
                .ignoreWidth(Boolean.TRUE.equals(config.getIgnoreWidth()))
                .ignoreRepeat(Boolean.TRUE.equals(config.getIgnoreRepeat()))
                .init();
    }
}
