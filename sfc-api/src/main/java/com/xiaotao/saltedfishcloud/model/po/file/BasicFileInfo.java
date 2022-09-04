package com.xiaotao.saltedfishcloud.model.po.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BasicFileInfo {
    public final static int TYPE_DIR = 1;
    public final static int TYPE_FILE = 2;
    protected String name;
    protected String md5;
    protected Integer type;
    protected long size;

    public BasicFileInfo(String name, String md5) {
        this.name = name;
        this.md5 = md5;
    }

    @JsonIgnore
    public boolean isFile() {
        return size != -1L || (type != null && type == TYPE_FILE);
    }

    public boolean isDir() {
        return !isFile();
    }

    /**
     * 获取文件后缀名，不带点.
     * @return 后缀名
     */
    public String getSuffix() {
        return FileUtils.getSuffix(name);
    }
}
