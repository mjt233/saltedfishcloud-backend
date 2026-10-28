package com.sfc.download.engine;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.ConfigNode;
import com.sfc.download.model.DownloadTaskParams;

import java.util.List;
import java.util.Set;

/**
 * 下载引擎：一种下载类型的具体执行实现。
 *
 * <p><b>扩展方式：</b>新增一种下载方式时，实现本接口并注册为 Spring Bean 即可；任务处理器通过
 * {@link DownloadEngineRegistry} 按类型路由，业务接口与任务调度逻辑均无需改动。</p>
 *
 * <p><b>类型推断：</b>「按下载地址推断下载类型」同样由引擎承担——实现类通过
 * {@link #matchesSource(DownloadType, String)} 声明自己能以哪些类型处理哪类来源，
 * {@link DownloadEngineRegistry#detect(String)} 汇总各引擎的判定结果，供前端创建任务前自动选型。</p>
 *
 * <p><b>生命周期与并发：</b>引擎是 Spring 单例，会被 Worker 线程池并发调用，因此实现类
 * <b>不得持有任务级可变状态</b>——一次执行的全部状态都保存在参数 {@link DownloadContext} 中。</p>
 *
 * <p><b>执行约定：</b></p>
 * <ul>
 *   <li>{@link #validate(DownloadTaskParams)} 在<b>创建任务时</b>调用，用于快速失败（不发起网络请求）；</li>
 *   <li>{@link #execute(DownloadContext)} 在<b>任务执行时</b>调用，必须把产物写到
 *       {@link DownloadContext#getTempDir()} 之下，并在耗时步骤中通过
 *       {@link DownloadContext#checkInterrupted()} 响应中断；</li>
 *   <li>中断时抛 {@code SfcTaskInterruptedException}（由 Worker SDK 上报为取消），其余失败直接抛运行时异常。</li>
 * </ul>
 */
public interface DownloadEngine {

    /**
     * 获取引擎唯一标识。
     *
     * <p>取值为简短的小写标识（例如 {@code http}、{@code git}、{@code aria2}、{@code ed2k}），用于在
     * 注册表、启动日志、任务日志与能力声明中标识「是哪个引擎实现」。要求：</p>
     * <ul>
     *   <li>非空且全局唯一，<b>不得</b>与其它引擎重复（重复会导致服务启动失败）；</li>
     *   <li>同一引擎支持多种下载类型时（例如 aria2 同时承载磁力链与种子），标识描述的是<b>引擎实现</b>，
     *       因此不要求与 {@link DownloadType#getCode()} 相同，也不要求一一对应。</li>
     * </ul>
     *
     * @return 引擎唯一标识，永不为 {@code null} 或空白
     */
    String getEngineId();

    /**
     * 声明本引擎支持的下载类型集合。
     *
     * <p>一个引擎可以支持多种类型（例如 aria2 同时支持磁力链与种子）；
     * 同一类型只能被一个引擎声明。</p>
     *
     * @return 支持的下载类型集合，不能为 {@code null}
     */
    Set<DownloadType> supports();

    /**
     * 判断本引擎能否以指定下载类型处理给定来源，用于「按下载地址推断类型」。
     *
     * <p>实现要求：</p>
     * <ul>
     *   <li>只做<b>纯语法判断</b>，不得发起网络请求、不得读取外部配置；</li>
     *   <li>只对 {@link #supports()} 中声明过的类型返回 {@code true}；</li>
     *   <li>判断应当宽松（用于给出候选类型），真正的来源合法性仍由
     *       {@link com.sfc.download.engine.DownloadSourceResolver#validateSource(DownloadType, DownloadTaskParams)}
     *       在创建任务阶段校验。</li>
     * </ul>
     *
     * <p>默认返回 {@code false}，即「不参与类型推断」；需要被自动识别的新引擎应覆盖本方法。</p>
     *
     * @param type   待判定的下载类型（必为 {@link #supports()} 的成员）
     * @param source 下载来源（通常是用户填写的下载地址），允许为 {@code null} 或空白
     * @return 本引擎能以该类型处理该来源时返回 {@code true}
     */
    default boolean matchesSource(DownloadType type, String source) {
        return false;
    }

    /**
     * 创建任务阶段校验参数。
     *
     * <p>只做「提交前即可判定」的校验（地址格式、选项取值、必填项等），<b>不应发起网络请求</b>；
     * 校验失败抛业务异常，前端直接收到 400，不会产生一条必然失败的异步任务。</p>
     *
     * @param params 下载任务参数
     * @throws SfcJsonException 参数不合法时抛出（HTTP 400 语义）
     */
    void validate(DownloadTaskParams params);

    /**
     * 执行下载，返回本地产物列表。
     *
     * <p>产物必须位于 {@link DownloadContext#getTempDir()} 之下，并可通过
     * {@link DownloadArtifact#of(java.nio.file.Path, java.nio.file.Path)} 构造；
     * 落盘（上传网盘）由调用方统一完成。</p>
     *
     * <p><b>产物路径契约（落盘方据此推断网盘顶层目录名，务必遵守）：</b></p>
     * <ul>
     *   <li><b>单文件</b>：只有一个产物，且 {@link DownloadArtifact#relativePath()} 直接是文件名，
     *       例如 {@code movie.mp4}；</li>
     *   <li><b>多文件 / 目录型产物</b>：全部 {@code relativePath} 必须共享同一个首层目录，
     *       例如 {@code <仓库名>/a.txt}、{@code <种子名>/b/c.txt}——该首层目录会作为网盘中的顶层目录名；</li>
     *   <li><b>不允许</b>返回多个顶层平铺的文件（例如 {@code a.txt} 与 {@code b.txt}）：此时无法推断顶层目录名，
     *       落盘方会退化为毫秒时间戳目录。</li>
     * </ul>
     *
     * @param context 执行上下文（参数、进度、日志、中断协作与临时目录）
     * @return 产物列表；成功但无产物时返回空列表（由调用方判定为失败）
     */
    List<DownloadArtifact> execute(DownloadContext context);

    /**
     * 获取当前不可用的原因。
     *
     * <p>由能力声明接口与创建任务的快速失败共用：返回 {@code null} 表示当前可用；
     * 非空表示本实例暂时无法执行该类型（例如依赖的外部程序未启用），内容会直接展示给用户。</p>
     *
     * @return 不可用原因；可用时返回 {@code null}
     */
    default String unavailableReason() {
        return null;
    }

    /**
     * 获取提交到 core 异步任务系统时使用的任务名称。
     *
     * @return 任务名称，默认「离线下载」
     */
    default String taskName() {
        return "离线下载";
    }

    /**
     * 获取提交异步任务时使用的 CPU 开销指数。
     *
     * @return CPU 开销指数，默认 20
     */
    default int cpuOverhead() {
        return 20;
    }

    /**
     * 声明本引擎支持的选项元数据，用于能力声明接口给前端渲染表单。
     *
     * <p>每个选项用一个 {@link ConfigNode} 描述：{@code name} 是 {@link DownloadTaskParams#options}
     * 中的键名，{@code title} 是面向用户的展示名，{@code inputType} 决定输入控件（取值见
     * {@code com.sfc.common.constant.ConfigInputType}），{@code defaultValue} 为字符串形式的默认值，
     * {@code describe} 为说明文案，{@code mask} 用于凭证类选项打码，{@code sort} 决定展示顺序。</p>
     *
     * @return 选项元数据列表；无专属选项时返回空列表
     */
    default List<ConfigNode> options() {
        return List.of();
    }
}
