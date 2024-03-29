package com.xiaotao.saltedfishcloud.utils.captcha;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import com.xiaotao.saltedfishcloud.model.CaptchaInfo;
import com.xiaotao.saltedfishcloud.service.CaptchaGenerator;

import java.awt.image.BufferedImage;
import java.util.Properties;

public class CaptchaGeneratorDefaultImpl implements CaptchaGenerator {
    private static final DefaultKaptcha KAPTCHA;
    static {
        DefaultKaptcha dk = new DefaultKaptcha();
        Properties properties = new Properties();
        properties.put("kaptcha.border", "no");
        properties.put("kaptcha.border.color","105,179,90");
        properties.put("kaptcha.textproducer.font.color","black");
        properties.put("kaptcha.image.width","140");
        properties.put("kaptcha.image.height","40");
        properties.put("kaptcha.textproducer.font.size","30");
        properties.put("kaptcha.session.key","code");
        properties.put("kaptcha.textproducer.char.length","6");
        properties.put("kaptcha.textproducer.char.space","4");
        properties.put("kaptcha.textproducer.font.names","宋体,楷体,微软雅黑");
        Config config = new Config(properties );
        dk.setConfig(config);
        KAPTCHA = dk;
    }

    /**
     * 生成一个验证码信息
     * @return 验证码信息绑定对象
     */
    public CaptchaInfo generate() {
        String text = KAPTCHA.createText();
        BufferedImage image = KAPTCHA.createImage(text);
        return new CaptchaInfo(text, image);
    }
}
