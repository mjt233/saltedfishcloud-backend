package com.xiaotao.saltedfishcloud.service.file.filesystem;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileDCInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @TODO 增加基于节点ID操作的方法以避免通过路径查询节点ID
 */
public interface DiskFileSystem {
    int SAVE_COVER = 0;
    int SAVE_NEW_FILE = 1;
    int SAVE_NOT_CHANGE = 2;

    /**
     * 解压一个压缩包到指定目录下
     * @param uid   用户ID
     * @param path  压缩包所在路径
     * @param name  压缩包名称
     * @param dest  解压目的地
     */
    void extractArchive(int uid, String path, String name, String dest) throws IOException;
    /**
     * 判断给定的路径是否存在
     * @param uid   用户ID
     * @param path  要判断的文件路径
     * @return      结果，存在则返回true，否则为false
     */
    boolean exist(int uid, String path);

    /**
     * 从文件系统获取文件资源
     * @param uid   用户ID
     * @param path  文件所在路径
     * @param name  文件名
     * @return      文件资源，若不存在则为null
     */
    Resource getResource(int uid, String path,String name);


    /**
     * 在网盘中连同所有父级目录，创建一个目录
     * @param uid   用户ID
     * @param path  网盘目录完整路径
     * @return 目录创建后，该目录的节点ID
     * @throws JsonException 目录树中某个部分与文件名冲突时抛出
     */
    String mkdirs(int uid, String path) throws IOException;

    /**
     * 通过文件MD5获取一个存储在系统中的文件<br>
     * @param md5   文件MD5值
     * @return      文件信息，path为本地文件系统中的实际存储文件路径，文件名将被重命名为md5+原文件拓展名
     * @throws NoSuchFileException  没有文件时抛出
     */
    FileInfo getFileByMD5(String md5) throws IOException;

    /**
     * 复制指定用户的文件或目录到指定用户的某个目录下
     * @param uid
     *      原资源的用户ID
     * @param source
     *      要操作的文件所在的网盘目录
     * @param target
     *      复制到的目的地目录
     * @param targetUid
     *      目标用户ID
     * @param sourceName
     *      源文件或目录名
     * @param targetName
     *      目标文件或目录名，
     * @param overwrite
     *      是否覆盖
     */
    void copy(int uid, String source, String target, int targetUid, String sourceName, String targetName, Boolean overwrite) throws IOException;

    /**
     * 移动网盘中的文件或目录到指定目录下
     * @param uid       用户ID
     * @param source    要被移动的网盘文件或目录所在目录
     * @param target    要移动到的目标目录
     * @param name      文件名
     * @param overwrite 是否覆盖原文件
     */
    void move(int uid, String source, String target, String name, boolean overwrite) throws IOException;

    /**
     * 获取某个用户网盘目录下的所有文件信息
     * 若路径不存在则抛出异常
     * 若路径指向一个文件则返回null
     * 若路径指向一个目录则返回一个集合，数组下标0为目录，1为文件
     * @param uid   用户ID
     * @param path  网盘路径
     * @return      一个List数组，数组下标0为目录，1为文件，或null
     */
    List<FileInfo>[] getUserFileList(int uid, String path) throws IOException;

    /**
     * 获取用户所有文件信息<br>
     * 默认正序为根目录优先，倒序为最深级目录优先
     * @param uid   用户ID
     * @param reverse 目录排序倒序
     * @return      文件信息集合，key为目录名，value为该目录下的文件信息列表
     */
    LinkedHashMap<String, List<FileInfo>> collectFiles(int uid, boolean reverse);

    /**
     * 通过节点ID获取节点下的文件信息
     * @param uid       用户ID
     * @param nodeId    节点ID
     * @return          一个List数组，数组下标0为目录，1为文件，或null
     */
    List<FileInfo>[] getUserFileListByNodeId(int uid, String nodeId);

    List<FileInfo> search(int uid, String key);

    /**
     * 通过移动本地文件的方式存储文件
     * @param uid               用户ID
     * @param nativeFilePath    本地文件路径
     * @param path              网盘路径
     * @throws IOException      存储出错
     */
    void moveToSaveFile(int uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException;

    /**
     * 保存数据流的数据到网盘系统中
     * @param uid         用户ID 0表示公共
     * @param stream      要保存的数据流
     * @param path        文件要保存到的网盘目录
     * @param fileInfo    文件信息
     * @throws IOException 文件写入失败时抛出
     */
    int saveFile(int uid,
                 InputStream stream,
                 String path,
                 FileInfo fileInfo) throws IOException;

    /**
     * 保存上传的文件到网盘系统中
     * @param uid         用户ID 0表示公共
     * @param file        接收到的文件对象
     * @param requestPath 请求的文件路径
     * @param md5         请求时传入的文件md5
     * @return 1
     * @throws IOException 文件写入失败时抛出
     */
    int saveFile(int uid,
                 MultipartFile file,
                 String requestPath,
                 String md5) throws IOException;

    /**
     * 创建文件夹
     * @param uid 用户ID 0表示公共
     * @param path 请求的路径
     * @param name 文件夹名称
     * @throws NoSuchFileException 当目标目录不存在时抛出
     */
    void mkdir(int uid, String path, String name) throws IOException;

    /**
     * 删除文件
     * @param uid   用户ID 0表示公共
     * @param path  请求路径
     * @param name  文件名列表
     * @throws NoSuchFileException 当目标路径不存在时抛出
     * @return 删除的数量
     */
    long deleteFile(int uid, String path, List<String> name) throws IOException;

    /**
     * 重命名文件或目录
     * @param uid 用户ID 0表示公共
     * @param path 文件所在路径（相对用户网盘目录）
     * @param name 被操作的文件名或文件夹名
     * @throws NoSuchFileException 当目标路径不存在时抛出
     * @param newName 新文件名
     */
    void rename(int uid, String path, String name, String newName) throws IOException;

    /**
     * 获取网盘中文件的下载码
     * @param uid 用户ID
     * @param path 文件所在网盘目录
     * @param fileInfo 文件信息
     * @param expr  下载码有效时长（单位：天），若小于0，则无限制
     */
    @SuppressWarnings("all")
    default String getFileDC(int uid, String path, BasicFileInfo fileInfo, int expr) throws IOException {
        Path localPath = Paths.get(DiskConfig.getPathHandler().getStorePath(uid, path, fileInfo));
        if ( !Files.exists(localPath) ){
            throw new JsonException(404, "文件不存在");
        }
        FileDCInfo info = new FileDCInfo();
        info.setDir(path);
        info.setMd5(fileInfo.getMd5());
        info.setName(fileInfo.getName());
        info.setUid(uid);
        String token = JwtUtils.generateToken(MapperHolder.mapper.writeValueAsString(info), expr < 0 ? expr : expr*60*60*24);
        return token;
    }
}
