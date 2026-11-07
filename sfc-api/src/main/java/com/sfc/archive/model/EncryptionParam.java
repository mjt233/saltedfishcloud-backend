package com.sfc.archive.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 压缩包加密参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionParam {
    /**
     * 加密算法标识，例如 AES。
     */
    private String algorithm;

    /**
     * 压缩包加密/解密密码。
     */
    private String password;
}

