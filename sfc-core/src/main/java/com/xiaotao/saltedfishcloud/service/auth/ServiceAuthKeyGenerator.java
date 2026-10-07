package com.xiaotao.saltedfishcloud.service.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 委托令牌签名密钥生成器。
 * <p>
 * 打印一份可直接粘贴进配置或环境变量的 <b>PKCS#8 / RSA-2048</b> 私钥（单行 base64(DER)），
 * 以及由它派生的公钥指纹（即 {@code kid}）。
 * </p>
 * <p>
 * 之所以提供这个类而不是只给 openssl 命令：Windows 下没有 {@code base64 -w0}，
 * PEM 多行文本放进环境变量还要处理换行，容易手搓出错。等价命令为：
 * </p>
 * <pre>{@code
 * openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out sfc-service-auth.pem
 * openssl pkcs8 -topk8 -nocrypt -in sfc-service-auth.pem -outform DER | base64 -w0
 * }</pre>
 * <p>
 * ⚠️ <b>私钥不得提交进仓库</b>：生产环境只通过环境变量 {@code SERVICE_AUTH_SIGNING_KEY} 注入，
 * 配置文件中只保留空占位符。集群部署时所有 core 节点必须使用同一把密钥。
 * </p>
 */
public final class ServiceAuthKeyGenerator {

    /** 生成密钥的位数 */
    private static final int KEY_BITS = 2048;

    private ServiceAuthKeyGenerator() {
    }

    /**
     * 生成并打印一把可用于 {@code sys.service.auth.signing-key} 的私钥。
     *
     * @param args 忽略
     * @throws Exception 密钥生成或摘要计算失败时抛出
     */
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_BITS, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();

        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String kid = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(keyPair.getPublic().getEncoded()));

        System.out.println("==== 委托令牌签名密钥（RSA-" + KEY_BITS + " / PKCS#8）====");
        System.out.println();
        System.out.println("写入 sys.service.auth.signing-key，或注入环境变量 SERVICE_AUTH_SIGNING_KEY：");
        System.out.println();
        System.out.println(privateKeyBase64);
        System.out.println();
        System.out.println("公钥指纹（kid，由密钥自动派生，无需配置）：" + kid);
        System.out.println();
        System.out.println("==== 注意 ====");
        System.out.println("1. 请勿提交进仓库；生产环境只用环境变量注入。");
        System.out.println("2. 集群部署时所有 core 节点必须配置同一把密钥，否则签发节点与 JWKS 节点不一致会导致验签失败。");
    }
}
