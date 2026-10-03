# sfc-ext-nacos 依赖冲突调试手册

> **适用**：为 `sfc-ext-nacos` 插件定位并收敛 `nacos-client` 传递依赖与宿主的冲突
> **性质**：档位 1 的核心练习。脚手架由我提供，**`exclusions` 清单的逐个定位由你完成**
> **配套**：[档位 1 设计方案](microservice-phase1-design.md)
> **创建日期**：2026-02-12

---

## 0. 先读这一页

这份手册的结论可以用三句话概括：

1. **粗粒度冲突（jar 名 + 版本）不会让应用起不来**——它只是**静默跳过**那个 jar，改用宿主的同名 jar
2. **类级重复才会让应用起不来**——报「存在以下冲突class」后直接抛异常
3. **所以真正的危险是"启动成功但运行期崩"**——被跳过的 jar 退回宿主版本后 API 不兼容，编译和启动都发现不了

因此：**"启动成功"不是通过标准，"能真正注册到 Nacos"才是。**

---

## 1. 机制：两级校验

代码位置：`sfc-core/.../ext/DefaultPluginManager.java`

### 第一级：`checkLibDependence()` —— 粗粒度预过滤

**输入**：jar 的 URL。**解析规则**（见 `parseJarNameByUrl()` 与 `getJarDependenceInfoFromName()`）：

```java
JAR_NAME_PATTERN    = "(?<=/).*?(?=.jar)"   // 从 URL 取文件名（不含 .jar）
JAR_VERSION_PATTERN = "(?<=-)\\d.*"          // 从文件名取【最后一个】匹配作为版本
```

例：`jackson-databind-2.19.0.jar` → `name = "jackson-databind"`、`version = "2.19.0"`。

**比对基准** `registeredDependencies` 由 `init()` 扫描 `masterLoader.getResources("META-INF")` 填充——
**即宿主 classpath 上的全部 jar**。启动日志会打印数量：

```
[插件系统]识别的初始Jar包依赖数量：N，成功注册数量：M
```

**判定**：

| 情况 | 异常 | 后果 |
|---|---|---|
| 名称 + 版本**完全相同** | `PluginDependenceRepeatException` | warn → **跳过该 jar**（`continue`） |
| 名称相同、**版本不同** | `PluginDependenceConflictException` | warn → 记入 `conflictList` 并 **跳过该 jar**（`continue`） |

> ⚠️ **注意这一级不抛异常给调用方**。它只影响"这个 jar 加不加入类加载器"。
> 被跳过后，由于合并类加载器是 **parent-first**（`DefaultPluginClassLoader extends URLClassLoader`），
> 这些类会由宿主的同名 jar 提供。

### 第二级：`ClassUtils.validUrl()` —— 类级硬门禁

```java
// 打开 jar，遍历所有 .class
URL resource = loader.getResource(name);
if (resource != null) {          // ← 该类【已能被合并类加载器解析到】
    names.add(className);
}
// ...
return names.isEmpty() ? Result.success()
                       : Result.<List<String>,URL>builder().isSuccess(false).data(names).param(url).build();
```

`loader` 是合并类加载器。parent-first 意味着 `getResource()` 会命中**宿主 classpath 上的一切**，以及**先前已加载插件**的内容。

**只要 jar 里有任意一个类已存在 → 判定失败 → 进 `errorList`**：

```
if (!errorList.isEmpty()) {
    ... log.error("[插件系统]外部插件无法加载: {}，存在以下冲突class：{}", ...)
    ... log.error("[插件系统]以下插件依赖无法被加载：\n{}", ...)
    throw new IllegalArgumentException("插件依赖无法加载：\n" + failJar);
}
```

**这是唯一会让应用起不来的路径。**

### 关键不对称

| | 第一级（版本冲突） | 第二级（类重复） |
|---|---|---|
| 是否中止启动 | ❌ 否 | ✅ **是** |
| 日志级别 | `warn` | `error` |
| 排查难度 | 高（要自己判断 API 兼容性） | 低（日志直接列出冲突类所属 jar） |

**反直觉之处**：**warning 比 error 更难处理**。error 会明确告诉你哪个 jar 有问题；warning 只告诉你"跳过了"，兼容性后果要等到运行期才浮现。

---

## 2. 日志速查

全部以 `[插件系统]` 为前缀。用这个列表判断当前处于什么状态：

| 日志片段 | 级别 | 含义 | 你要做什么 |
|---|---|---|---|
| `识别的初始Jar包依赖数量：N` | info | 宿主 jar 清单已建立 | 记下 N，确认不为 0 |
| `初始依赖存在重复：` | warn | 宿主自身有重复 jar | 通常可忽略 |
| `初始依赖存在版本冲突：` | warn | 宿主自身有版本冲突 | 与插件无关，但会降低判定准确度 |
| `依赖包版本出现重复，已忽略：` | warn | 插件 jar 与宿主同名同版本 → 跳过 | **无需处理**，宿主版本等价 |
| `依赖包版本出现冲突：` | warn | 插件 jar 与宿主同名不同版本 → 跳过 | ⚠️ **逐条评估宿主版本 API 是否兼容** |
| `存在以下冲突class：` | error | 类级重复 → **启动将失败** | ⚠️ **把该 jar 加入 `<exclusions>`** |
| `以下插件依赖无法被加载：` | error | 失败的 jar 汇总 | 同上，这是待处理清单 |
| `插件 xxx 加载失败` | error | 插件注册阶段抛异常 | 看前面的根因 |

**Grep 建议**：

```powershell
# 只看插件系统的依赖相关日志
Select-String -Path .\log\output.log -Pattern '插件系统.*(依赖|冲突class)'
```

---

## 3. 两类失败模式

| | 模式 A：启动失败 | 模式 B：运行期崩溃 |
|---|---|---|
| **触发** | 第二级 `validUrl` 命中类重复 | 第一级跳过了某个 jar，宿主版本 API 不兼容 |
| **时机** | 进程启动阶段 | 调用 Nacos 时（注册/发现） |
| **日志** | `存在以下冲突class` + `插件依赖无法加载` | `NoSuchMethodError` / `NoSuchFieldError` / `ClassNotFoundException` / `NoClassDefFoundError` |
| **排查** | 容易——日志直接给出类名 | 困难——需反查"哪个 jar 被跳过了" |
| **对策** | 把报出的 jar 加 `<exclusion>` | 不能简单排除。见 §6 |

**模式 B 的定位方法**：先按 §2 找出所有「依赖包版本出现冲突」的 warn 日志，列出被跳过的 jar 清单；再对照运行期异常里的类名，判断该类属于哪个被跳过的 jar。

---

## 4. 定位工具

### 4.1 生成宿主的依赖清单

```powershell
mvn -q -pl sfc-core dependency:list -DincludeScope=runtime -DoutputFile=host-deps.txt
```

> `sfc-core` 是主应用，其 runtime 依赖即宿主 classpath 的近似。用 `dependency:list` 而非 `dependency:tree`，因为我们要的是**扁平的 artifact 列表**，正好对应"宿主有哪些 jar"。

### 4.2 生成插件的 `plugin-lib`

```powershell
mvn -q -pl sfc-ext/sfc-ext-nacos -am compile
```

> **必须用 `mvn compile`，不能用 IDE 的 Build。**
> `plugin-lib/` 由 `maven-dependency-plugin:copy-dependencies` 在 **`compile` 阶段**生成到
> `sfc-ext-nacos/target/classes/plugin-lib`。IDE 的增量编译通常**不会**触发这个 Maven 插件，
> 导致你改了 `<exclusions>` 却看不到任何变化。
>
> `-am`（also-make）会连带构建 `sfc-cloud-api`、`sfc-api` 等上游模块。

### 4.3 对比脚本

下面这段 PowerShell **复刻了项目自己的 jar 名解析规则**（`(?<=-)\d.*` 取最后一个匹配），
因此结论与 `checkLibDependence()` 一致。

```powershell
$ErrorActionPreference = 'Stop'

# ── 复刻 DefaultPluginManager.getJarDependenceInfoFromName() ──
function Get-JarIdentity {
    param([string]$FileName)
    $base = $FileName -replace '\.jar$', ''
    $m = [regex]::Matches($base, '(?<=-)\d.*')
    if ($m.Count -eq 0) { return [pscustomobject]@{ Name = $base; Version = $null } }
    $ver = $m[$m.Count - 1].Value
    return [pscustomobject]@{
        Name    = $base.Substring(0, $base.Length - $ver.Length - 1)
        Version = $ver
    }
}

# ── 插件 plugin-lib ──
$pluginLib = 'sfc-ext\sfc-ext-nacos\target\classes\plugin-lib'
if (-not (Test-Path $pluginLib)) { throw "未找到 $pluginLib，请先执行 §4.2 的 mvn compile" }
$pluginJars = Get-ChildItem $pluginLib -Filter *.jar | ForEach-Object { Get-JarIdentity $_.Name }

# ── 宿主依赖（groupId:artifactId:type:version:scope）──
$hostJars = Get-Content 'host-deps.txt' |
    Where-Object { $_ -match ':' -and $_ -notmatch '^\[' } |
    ForEach-Object {
        $p = $_ -split ':'
        if ($p.Count -ge 4 -and $p[2] -eq 'jar') { Get-JarIdentity ($p[1] + '-' + $p[3]) }
    }

$hostMap = @{}
foreach ($h in $hostJars) { $hostMap[$h.Name] = $h.Version }

# ── ① 会被"跳过"的插件 jar（第一级命中）──
Write-Host "`n=== ① 与宿主同名：将被跳过（第一级）===" -ForegroundColor Yellow
$skipped = foreach ($p in $pluginJars) {
    if ($hostMap.ContainsKey($p.Name)) {
        [pscustomobject]@{
            名称     = $p.Name
            插件版本 = $p.Version
            宿主版本 = $hostMap[$p.Name]
            判定     = if ($hostMap[$p.Name] -eq $p.Version) { '完全重复(无害)' } else { '版本冲突(运行期风险)' }
        }
    }
}
$skipped | Sort-Object 判定, 名称 | Format-Table -AutoSize

# ── ② 会被"加载"的插件 jar（需通过第二级）──
Write-Host "`n=== ② 宿主没有：将被加载（需通过类级校验）===" -ForegroundColor Cyan
$pluginJars | Where-Object { -not $hostMap.ContainsKey($_.Name) } |
    Sort-Object Name | Format-Table Name, Version -AutoSize
```

**怎么读输出**：

- **① 的「版本冲突(运行期风险)」** → 候选 `<exclusion>`。判断准则见 §5
- **① 的「完全重复(无害)」** → 不用管
- **② 的清单** → 这些 jar 会被真正加载，它们的类**不能**与宿主重复。**但你不需要自己算**——一旦重复，应用启动日志会直接列出冲突类（见 §1 第二级）

> **重要简化**：第二级（类级）校验的冲突清单**由应用启动日志直接给出**，无需自己实现 class 扫描。
> §4.3 脚本的价值在于处理**第一级**——那一级只给 warning，不给"怎么办"。

---

## 5. `<exclusion>` 决策准则

对 §4.3 输出中每一条「版本冲突」：

| 判断 | 动作 |
|---|---|
| 该库是**通用基础设施**（日志、JSON、集合工具、YAML 等），宿主版本**更新或同代** | ✅ **排除**。用宿主版本 |
| 该库是 **nacos-client 的核心协议依赖**（`nacos-api`、`nacos-common`、`nacos-auth-plugin`、gRPC、protobuf 等） | ❌ **不要排除**。宿主没有，必须随插件加载 |
| 宿主版本**明显落后**于插件需要的版本，且该库 API 有过破坏性变更 | ⚠️ **不要排除**，但会撞第二级 → 转 §6 shade |
| 看不出结论 | 先**不排除**，跑一次启动，让日志告诉你答案 |

**经验起点**（**必须用 §4.3 实测确认，不要照抄**）：JSON / 日志 / YAML 类通常与宿主重叠，值得优先排查。

`pom.xml` 中的写法：

```xml
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>...</version>
    <exclusions>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </exclusion>
        <!-- 按实测结果继续追加 -->
    </exclusions>
</dependency>
```

> **排除过度**同样有害：排掉一个宿主**没有**的库 → 运行期 `ClassNotFoundException`。
> 这就是为什么每次调整后都必须做**"能真正注册到 Nacos"**的验证，而不是只看启动成功。

---

## 6. 何时升级到 shade relocate

**触发条件**（任一）：

- 某个 jar **既不能排除**（nacos-client 需要它的特定版本）**又与宿主类重复** → 第二级必然失败
- 「版本冲突」条目过多（> 5~6 条），手工维护成本超过收益

**配置要点**（`maven-shade-plugin`）：

| 要点 | 说明 |
|---|---|
| `relocation` | 把 nacos 相关包整体重定位，如 `com.alibaba.nacos` → `com.sfc.nacos.shaded.alibaba.nacos` |
| `ServicesResourceTransformer` | **必须**。合并/改写 `META-INF/services/*`，否则 SPI 全断 |
| `AppendingTransformer` | 处理 `META-INF/spring.factories`、`META-INF/spring/*.imports` 等需合并的资源 |
| `artifactSet` | 精确限定只 shade 依赖，**不要**把插件自身 class 也卷进去 |
| 产物位置 | 生成的 fat-jar 需落入 `plugin-lib/`，否则 `getPluginDependencies()` 读不到 |
| jar 文件名 | 文件名决定第一级判定。**建议不带版本号或使用唯一名**，避免与宿主同名被判冲突 |

**⚠️ shade 方案的最大不确定性**：

> `relocate` 只能改写**字节码中的符号引用**，**改不掉代码里的字符串常量**。
> 若 nacos-client 内部存在 `Class.forName("com.alibaba.nacos...")`、
> 或从 `META-INF/nacos/...` 读取配置、或通过反射按全限定名加载，
> 这些**不会**被重定位，会以 `ClassNotFoundException` 的形式在运行期暴露。

因此：**先穷尽 `exclusions`，再考虑 shade。** 多数情况下 `exclusions` 就够。

---

## 7. 操作要点（最容易浪费时间的两件事）

### 7.1 在 `exclusions` 调通前，不要把插件加进 `extra-resource`

**原因**：插件一旦被加载，其 `plugin-lib/` 依赖就**无条件**进入两级校验流程，
**与 `sys.service.registry-provider` 的取值无关**。

即：即使 `registry-provider` 仍是 `cache`（Nacos 完全没启用），
只要 `plugin.extra-resource` 里出现了 `sfc-ext-nacos`，类级冲突照样让应用起不来。

> 如果你不知道这一点，会陷入"我明明没启用 Nacos，为什么启动失败"的困惑。

**正确的迭代顺序**：

```
1. 先让项目在 registry-provider: cache 下正常运行     ← Step 1~4 的成果，零风险
2. mvn -pl sfc-ext/sfc-ext-nacos -am compile          ← 生成 plugin-lib
3. 跑 §4.3 脚本，为「版本冲突」条目补 <exclusion>
4. 把 sfc-ext-nacos 加入 plugin.extra-resource，启动
5. 若报「存在以下冲突class」→ 把报出的 jar 补进 <exclusion>，回到 2
6. 重复 2~5 直到启动成功
7. 【关键】切换 registry-provider: nacos，确认 Nacos 控制台出现 sfc-core 实例
   ← 只有这一步能发现模式 B（运行期 NoSuchMethodError）
```

### 7.2 改了 `<exclusions>` 必须重新 `mvn compile`

`plugin-lib/` 的内容由 `maven-dependency-plugin:copy-dependencies`（绑定 `compile` 阶段）决定。
IDE 的 Build 不会触发它。

**判断你踩了这个坑**：改了 pom、重启、日志里那几条「版本冲突」warn **一字不变**。

**规避**：每次调 `exclusions` 后固定执行

```powershell
mvn -q -pl sfc-ext/sfc-ext-nacos -am compile
```

> 这一点 `docs/develop/plugin/create-plugin.md` 也提到了：
> 「如果插件有配置第三方依赖，请务必对插件的maven模块执行 `mvn compile` 确保依赖库能得到加载」。

---

## 8. 完成标准

不要以"编译通过"或"进程起来了"作为完成标准。逐条确认：

- [ ] `mvn -pl sfc-ext/sfc-ext-nacos -am compile` 成功，`plugin-lib/` 中存在预期 jar
- [ ] 咸鱼云以 `registry-provider: cache` 启动**正常**（证明插件加入后未破坏既有功能）
- [ ] 日志中**无** `存在以下冲突class`、**无** `以下插件依赖无法被加载`
- [ ] 日志中每条「依赖包版本出现冲突」**都已评估**，并记录"为什么可以退回宿主版本"
- [ ] 切换到 `registry-provider: nacos` 后启动成功
- [ ] **Nacos 控制台出现 `sfc-core` 实例** ← 证明 nacos-client 在运行期真正工作
- [ ] `GET /api/admin/sys/service/list` 能返回该实例
- [ ] 无 `NoSuchMethodError` / `ClassNotFoundException` / `NoClassDefFoundError`

---

## 9. 速查表

| 现象 | 最可能原因 | 第一步 |
|---|---|---|
| 启动失败，报「存在以下冲突class」 | 类级重复（第二级） | 把报出的 jar 加 `<exclusion>` |
| 启动成功，调用 Nacos 时 `NoSuchMethodError` | 某 jar 被静默跳过，宿主版本 API 不兼容 | 查 warn 日志找被跳过的 jar |
| 改了 `<exclusions>` 但日志毫无变化 | IDE 编译未触发 `copy-dependencies` | `mvn -pl sfc-ext/sfc-ext-nacos -am compile` |
| 没启用 Nacos 却启动失败 | 插件已加入 `extra-resource`，校验与 provider 无关 | 先从 `extra-resource` 移除插件 |
| `plugin-lib` 目录不存在或是空的 | 未执行 `mvn compile` | §4.2 |
| 排除了某库后报 `ClassNotFoundException` | 排除过度，宿主没有这个类 | 撤销该条 `<exclusion>` |
| 客户端反复重连、注册不上 | Nacos 2.x gRPC 端口 9848 未暴露 | Docker 加 `-p 9848:9848` |
