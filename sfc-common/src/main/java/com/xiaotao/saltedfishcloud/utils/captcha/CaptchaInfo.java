package com.xiaotao.saltedfishcloud.utils.captcha;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.awt.image.BufferedImage;

@AllArgsConstructor
@Getter
public class CaptchaInfo {
    private String code;
    private BufferedImage image;
}
