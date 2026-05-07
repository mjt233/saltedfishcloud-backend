package com.saltedfishcloud.ext.ve.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import static com.saltedfishcloud.ext.ve.constant.VEConstants.EncoderType.*;

/**
 * 媒体流编码转换规则
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class EncodeConvertRule {
    /**
     * 输入流索引
     */
    private String index;

    /**
     * 处理方法,copy或convert
     * @see com.saltedfishcloud.ext.ve.constant.VEConstants.EncodeMethod
     */
    private String method;

    /**
     * 使用的编码器
     */
    private String encoder;

    /**
     * 流类型，见常量
     * @see com.saltedfishcloud.ext.ve.constant.VEConstants.EncoderType
     */
    private String type;

    /**
     * 比特率
     */
    private String bitRate;

    /**
     * 质量因子，通常18~23，该值越大画质越差，文件越小。
     */
    private String crf;

    /**
     * 视频编码的质量与速度预设。可选值从快到慢依次为：
     * <ul>
     *     <li><b>ultrafast</b> - 极速编码，压缩效率最低，输出文件最大，适用于实时流或测试</li>
     *     <li><b>superfast</b> - 超快速编码，适合需要快速转换的场景</li>
     *     <li><b>veryfast</b> - 非常快速，速度与质量的较好平衡，适合屏幕录制</li>
     *     <li><b>faster</b> - 较快编码，比veryfast稍慢但质量更好</li>
     *     <li><b>fast</b> - 快速编码，默认预设值，适合一般用途</li>
     *     <li><b>medium</b> - 中等速度，速度与质量的最佳平衡，<b>推荐作为通用选择</b></li>
     *     <li><b>slow</b> - 慢速编码，较高压缩效率，文件比medium小5-10%，适合高质量存储</li>
     *     <li><b>slower</b> - 更慢编码，压缩效率更高，编码时间显著增加</li>
     *     <li><b>veryslow</b> - 非常慢编码，最高压缩效率，输出文件最小，适合长期存档</li>
     *     <li><b>placebo</b> - 安慰剂模式，极慢编码，相比veryslow只有微小提升，通常不建议使用</li>
     * </ul>
     *
     * <p><b>预设选择原则：</b>预设值越慢，在相同视频质量下输出的文件越小，但编码所需时间越长。
     * 对于H.264/H.265编码器，veryslow相比ultrafast通常可节省30-50%的文件大小。</p>
     *
     * <p><b>推荐场景：</b></p>
     * <ul>
     *     <li>日常使用：medium + crf 23</li>
     *     <li>动画收藏：slow + crf 18 + tune animation</li>
     *     <li>网络传输：faster + crf 23 + maxrate限制</li>
     *     <li>实时录制：ultrafast + crf 28</li>
     *     <li>最终存档：veryslow + crf 17</li>
     * </ul>
     *
     * <p><b>注意：</b>此参数仅适用于软件编码器（如libx264、libx265），
     * 硬件编码器可能有不同的预设选项。</p>
     *
     * @see #crf 恒定质量因子参数，与preset配合使用
     * @see #tune 编码优化调优参数
     */
    private String preset;

    /**
     * 视频编码优化调优参数。针对特定类型的视频内容进行编码优化，以获得更好的压缩效率或视觉质量。
     *
     * <p><b>常用调优选项：</b></p>
     * <ul>
     *     <li><b>film</b> - 电影/真人影片优化。降低去块效应滤波强度，适合高细节的真人视频。</li>
     *     <li><b>animation</b> - 动画/卡通优化。使用更高的去块效应强度，减少色带效应，适合平面色彩区域多的动画。</li>
     *     <li><b>grain</b> - 胶片颗粒优化。保留胶片颗粒纹理，避免编码器过度平滑处理。</li>
     *     <li><b>stillimage</b> - 静态图像优化。针对幻灯片、演示文稿等静态内容优化。</li>
     *     <li><b>fastdecode</b> - 快速解码优化。禁用某些编码工具以减少解码复杂度，提升播放性能。</li>
     *     <li><b>zerolatency</b> - 零延迟优化。适用于实时流媒体和视频会议，减少编码延迟。</li>
     *     <li><b>psnr</b> - PSNR（峰值信噪比）优化。以PSNR为指标进行优化，主要用于编码测试。</li>
     *     <li><b>ssim</b> - SSIM（结构相似性）优化。以SSIM为指标进行优化，更符合人眼感知。</li>
     * </ul>
     *
     * <p><b>特殊调优选项（H.264/H.265）：</b></p>
     * <ul>
     *     <li><b>none</b> - 不使用任何调优</li>
     * </ul>
     *
     * <p><b>场景推荐：</b></p>
     * <ul>
     *     <li>日本动画/卡通：<code>animation</code></li>
     *     <li>真人电影/电视剧：<code>film</code></li>
     *     <li>老电影（有颗粒感）：<code>grain</code></li>
     *     <li>PPT演示/幻灯片：<code>stillimage</code></li>
     *     <li>视频会议/直播：<code>zerolatency</code></li>
     *     <li>低性能设备播放：<code>fastdecode</code></li>
     * </ul>
     *
     * <p><b>使用示例：</b></p>
     * <pre>
     * // 动画编码
     * -c:v libx264 -tune animation -crf 23
     *
     * // 真人电影
     * -c:v libx264 -tune film -crf 20
     *
     * // 实时流媒体
     * -c:v libx264 -tune zerolatency -preset ultrafast
     * </pre>
     *
     * <p><b>注意事项：</b></p>
     * <ul>
     *     <li>调优参数不是必需的，但使用合适的调优可以提升编码效率</li>
     *     <li>某些调优选项可能不兼容（如zerolatency与veryslow预设）</li>
     *     <li>调优效果与视频内容密切相关，建议根据实际内容选择</li>
     * </ul>
     *
     * @see #preset 编码速度预设参数
     * @see #crf 质量因子参数
     */
    private String tune;

    /**
     * 根据流类型，获取对应的编码器类型标志
     */
    public String getTypeFlag() {
        if (AUDIO.equals(type)) {
            return "a";
        } else if (VIDEO.equals(type)) {
            return "v";
        } else if (SUBTITLE.equals(type)) {
            return "s";
        } else if (ATTACHMENT.equals(type)) {
            return "t";
        } else if (DATA.equals(type)) {
            return "d";
        } else {
            throw new IllegalArgumentException("未知类型：" + type);
        }
    }
}
