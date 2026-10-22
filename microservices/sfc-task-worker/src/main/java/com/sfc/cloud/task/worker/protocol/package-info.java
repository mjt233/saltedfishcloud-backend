/**
 * 与 sfc-core 任务执行者内部协议一一对应的报文镜像。
 *
 * <p><b>唯一协议依据</b>：{@code sfc-core} 的
 * {@code com.sfc.task.controller.internal.TaskWorkerInternalController}（前缀
 * {@code /api/internal/task}，全部为 POST）与 {@code com.sfc.task.protocol.*} 下的 record。</p>
 *
 * <p>本包刻意<b>不依赖 sfc-core</b>（零宿主依赖），因此每个 DTO 都是 core 侧 record 的镜像：
 * <b>类型名、record 组件名（即 JSON 字段名）、组件顺序与可空性必须与 core 完全一致</b>，
 * 否则 Jackson 序列化/反序列化会静默丢字段，联调必然失败。修改 core 协议时必须同步修改本包。</p>
 */
package com.sfc.cloud.task.worker.protocol;
