package com.sfc.archive.annocation;

import com.sfc.archive.config.ArchiveAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用在线解压缩功能
 */
@Retention(RetentionPolicy.RUNTIME)
@Import(ArchiveAutoConfiguration.class)
@Target(ElementType.TYPE)
@Documented
public @interface EnableArchive {
}
