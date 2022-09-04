package com.xiaotao.saltedfishcloud.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.awt.image.BufferedImage;

@AllArgsConstructor
@Getter
public class CaptchaInfo {
    /**
     * 验证码内容
     */
    private final String code;

    /**
     * 图像
     */
    private final BufferedImage image;
}
