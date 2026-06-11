package com.sfc.dm.service.identify;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;

import java.io.File;
import java.util.List;

/**
 * 文件类型识别提供者接口
 */
public interface FileTypeCheckProvider {
    /**
     * 获取该Provider的唯一标识
     */
    String getId();

    /**
     * 获取该Provider检测的文件类型名称
     */
    String getTypeName();

    /**
     * 获取该Provider检测的文件类型标识
     */
    String getTypeId();

    /**
     * 获取该Provider支持的文件拓展名列表
     */
    List<String> getSupportedFileExtensions();

    /**
     * 获取该Provider支持提取的元数据定义列表
     */
    List<FileMetadataDefine> getMetadataDefines();

    /**
     * 对文件进行类型检测与元数据提取
     * @param file 待检测的本地临时文件
     * @param extraMetadata 是否提取元数据
     * @return 识别结果，null表示无法识别
     */
    FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata);
}
