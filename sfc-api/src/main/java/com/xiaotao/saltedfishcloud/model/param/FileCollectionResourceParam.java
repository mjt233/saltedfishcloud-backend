package com.xiaotao.saltedfishcloud.model.param;

import com.xiaotao.saltedfishcloud.model.dto.SubmitFile;
import com.xiaotao.saltedfishcloud.model.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.model.po.CollectionRecord;
import lombok.Data;

/**
 * 文件收集提交资源处理器参数
 */
@Data
public class FileCollectionResourceParam {

    /**
     * 资源操作类型
     */
    public enum Type {
        /**
         * 提交文件
         */
        SUBMIT,

        /**
         * 下载已提交的文件
         */
        GET
    }

    /**
     * 收集任务id
     */
    private Long cid;

    /**
     * 收集任务验证码
     */
    private String verification;

    /**
     * 提交的文件表单信息
     */
    private SubmitFile submitFile;

    /**
     * 操作类型
     */
    private Type type;

    /**
     * 文件收集任务对象
     */
    private CollectionInfo collectionInfo;

    /**
     * 提交记录
     */
    private CollectionRecord collectionRecord;
}
