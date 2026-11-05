# 睡个好觉 · 物理被子（睡姿贴合 + XPBD 布料）设计 / 实施计划

> 状态：**设计稿，未开工**
> 分支：`exp/maid-settings-gui`（沿用当前实验分支）
> 前置文档：`2026-11-03-lie-in-and-naps.md`（当时把「被子模型」列为不做，本计划接的就是那一条）
> 现存实现：`src/main/java/com/sleepwell/client/quilt/`（解析式外壳，非物理，见第二章）

---

## 一、主人原始需求（逐条落成验收项）

| # | 原话 | 落成的工程要求 | 验收方式 |
|---|---|---|---|
| R1 | 「只要是玩家可见的女仆模型按说都应该能被扫描」 | 碰撞体必须来自**这一帧真正参与渲染的骨头/方块**，不得来自模型文件静态解析，也不得来自 AABB | 5 个不同模型各躺一次，线框贴合；穿甲/藏手/藏腿后线框跟着变 |
| R2 | 「真实的物理效果」 | 被子是**逐质点解算的布料**，不是预烘焙的曲面；重力、约束、碰撞全在运行时算 | 换姿势、被碰、被子滑落都自然；无「硬壳感」 |
| R3 | 「凹陷和下垂都要自然」 | 需要弯曲约束 + 摩擦 + 预松弛 + 足够分辨率；缺一即视为不达标 | 手肘/膝盖处凹、身侧与床沿之间下垂、脚踝处塌陷 |
| R4 | 「部分女仆睡觉姿势和站立姿势不一样」 | 全域**姿态无关**：被子初始铺设、床面高度、碰撞体缓存都不得写死姿态 | winefox 三变体 + kluonoa + hailuo 五例通过 |
| R5 | 只在睡觉时出现 | 非睡觉状态零解算、零渲染、零开销 | 性能采样：醒着的女仆不产生布料 tick |

---

## 二、现状实测（必须先说清，否则改不动）

### 2.1 现有三个类各自在做什么

| 文件 | 行数 | 实际做法 |
|---|---|---|
| `client/quilt/QuiltRenderer.java` | 364 | 在 `RenderLevelStageEvent.AFTER_ENTITIES` 里用**解析式**造壳：12×6 网格，高度 = `body * cos(π·ratio)^0.30` 的圆顶 + 三道垂直墙（脚端封墙 `L299`、两侧下摆 `L325`），无重力、无碰撞、无时间演化 |
| `client/quilt/MaidSilhouette.java` | 197 | 反射拿模型里的 `*modelMap*` 字段，遍历部件原点（父级累加 x/y/z），算出 **24 片「高度 → 半径」旋转轮廓**，横向半径 = `√(x²+z²)/16 + 0.06`（`L143`），每 `REFRESH_TICKS = 10`（`L31`）采样一次 |
| `client/quilt/BedGeometry.java` | 80 | 反射猜字段（字段类型名里含 `Model`/`Bedrock`）取床模型的 `renderBoundingBox`，并按数值大小猜「像素还是格」（`L45`） |

### 2.2 问题清单（按工作区第 0 条先上报，未擅自改）

| 编号 | 位置 | 影响 | 建议 |
|---|---|---|---|
| Q1 | `MaidSilhouette.java:143` | 轮廓只有「骨头原点的横向距离」，**不含 cube 尺寸、不含旋转、不含缩放**，本质是「绕身体轴的旋转体」。手抬起、腿弯起、尾巴绕过来，全都没有信息 → 这就是被子只能做出圆顶的原因 | 改成逐 bone 的 **OBB → 胶囊**（本计划 M1/M2） |
| Q2 | `MaidSilhouette.java:189` | 只认 bedrock 模型（`modelMap` 反射）；TLM 1.5.3 主流是 **gecko 模型**（`GeckoEntityMaidRenderer`），走进去直接 `return null`，只在日志留一句 `no bedrock part map` | 两条路径都实现（M1） |
| Q3 | `MaidSilhouette.java:31` | `REFRESH_TICKS = 10`，姿势变了要 0.5 秒才更新；盖被子时翻身/呼吸都会滞后 | 物理要求 **每帧** 新碰撞体（M1） |
| Q4 | `MaidSilhouette.java:139` | `walk` 不检查 `visible` / `dontRender`，把「藏起来的腿、藏起来的手」也算进轮廓（穿甲、坐姿、被单裹住时尤其明显） | 过滤（M1） |
| Q5 | `BedGeometry.java:57` | 靠「字段类型名含 Model」猜，可能抓错字段；失败静默返回 null，被子随后按 `FALLBACK_TOP` 硬编码高度画 | 改为按块状态 + 已知渲染器类型取，失败要写一条**一次性日志**（M4） |
| Q6 | `QuiltRenderer.java:67` | `PROFILE` 是手工调的固定剖面 + `L82` 的 `FOOT_MARGIN = 0.95` 等一堆魔法数；换模型/换床色都要重调 | 物理接管后，这些常数只保留「布料尺寸与初始位置」用途（M5/M8） |

---

## 三、技术结论

1. **不用路线一**（`OutlineBufferSource` 像素轮廓）：它给的是光栅化后的四边形，没有表面、没有法线、没有最近点，布料要的「穿进去往哪推」它一个字都答不了
2. **路线二为地基**：渲染期抓 bone 世界矩阵 → 每个 cube 一个 OBB → 转**胶囊链** → 作为布料碰撞体
3. **布料用 XPBD**（Müller 2020），固定时间步 60 Hz × 4 子步，与帧率解耦
4. **被子活在世界空间**，不挂在任何 PoseStack 上；床是碰撞体，不是装饰
5. **纯客户端**：不解算在服务端、不同步粒子、不写 NBT；服务端将来若要玩法（保暖/隐蔽）只同步**布尔状态**
6. **现有解析式外壳保留为降级路径**：物理初始化失败（模型读不到、平台不支持）时退回今天这套壳，绝不出现「被子消失」

---

## 四、目标架构

```
com.sleepwell.client.quilt
├── config/            QuiltConfig（客户端配置读取，只读快照）
├── collider/
│   ├── QuiltCollider          record：胶囊(a,b,r) / 球(c,r) / AABB，带 hard|soft 分级
│   ├── ColliderCapture        每帧采集入口：bedrock 路径 + gecko 路径，写入 FrameColliders
│   ├── BedrockColliderSource  遍历 BedrockPart 树（public 字段，免反射）
│   ├── GeckoColliderSource    遍历 GeoBone / GeoMesh / GeoCube（M0 探针确认访问器）
│   └── FrameColliders         世界空间碰撞体快照 + 姿态指纹 + 复用的 float[] 缓冲
├── cloth/
│   ├── ClothGrid              粒子缓冲区（float[] pos/prev/acc，索引化，零分配）
│   ├── ClothConstraints       结构/剪切/弯曲约束表（int[] 四元组）
│   ├── XpbdSolver             子步循环：积分 → 约束 → 碰撞
│   ├── ClothCollision         胶囊/球/AABB 碰撞响应（推出 + 摩擦 + 速度钳制）
│   └── ClothState             每女仆状态：IDLE / SETTLING / SIMULATING / FADING
├── render/
│   ├── QuiltRenderer          改造：画 ClothGrid（顶点/法线/UV/光照），保留旧壳作 fallback
│   └── QuiltTexture           被面贴图与平铺（沿用 `assets/sleep_well/textures/quilt/*.png`）
└── debug/
    ├── QuiltDebugRenderer     F3 线框：胶囊、床 AABB、布料网格、法线
    └── QuiltDebugPanel        三个对齐旋钮（主轴偏移 / 床面偏移 / 被子尺寸）
```

**依赖方向**：`render → cloth → collider`；`collider` 不依赖 `render`。这样 M3 的布料核心可以脱离 Minecraft 单测。

---

## 五、算法与数据

### 5.1 碰撞体采集（R1 的核心）

**原则**：只采「这一帧真的被渲染的」骨头，且只采「真的画出东西」的 cube。

| 路径 | 采集时机 | 做法 | 过滤 |
|---|---|---|---|
| bedrock（`BedrockModel` / `BedrockPart`） | `RenderLivingEvent.Post`（模型已带睡姿） | 从 `modelMap` 的根部件递归：矩阵 = 父矩阵 × 本部件 `translateAndRotate`；cube 用 `BedrockCube` 的 min/max | 跳过 `visible == false`、`isEmpty()`、体积 < 1 px³ |
| gecko（`GeoReplacedEntityRenderer` / `GeoBone`） | 同帧 `RenderLivingEvent.Post` | 走 `GeoBone` 树：`pivot` / `rotation` / `cubes(GeoMesh)`；cube 的 OBB **用它的 quad 顶点反算 8 个角**（`GeoCube` 只有 `size` 与 `quads`，没有现成 min/max） | 跳过 `dontRender`、空 mesh |

- **M0 必须先探明**：`GeoBone` 没有缓存的世界矩阵（实测字段只有 parent/children/pivot/rotation/cubes/dontRender），矩阵是在渲染递归里现算的。M0 的任务就是确认「动画后的变换存在哪里」（`AnimatedGeoBone` 的 snapshot 或渲染递归参数），拿不到就退回自算 pivot 链 + snapshot 叠加，**这是本计划唯一的技术未知项**
- 世界矩阵换算链：模型空间 → 女仆局部（× `getHeightScale()` / `getWidthScale()`）→ 世界（床/女仆的 `PoseStack` 变换，睡姿旋转也在其中）
- **绝不复用 AABB**：`maid.getBoundingBox()` 在睡觉时经常还是站立尺寸（现有代码 `QuiltRenderer.java:197`、`:251` 已被这一点坑过，注释里写着）

### 5.2 胶囊化

```
给定 cube 的 8 个世界角点：取最长边方向为轴，端点 a/b 为两端面中心，半径 r = 垂直方向半宽
r = clamp(r, max(0.75 × spacing, 0.03), 0.25)
```
- 硬碰撞（capsule）：躯干、头、大腿小腿、上下臂、手
- 软碰撞（球，刚度低、半径大）：尾巴、辫子、下摆装饰、蝴蝶结
- 忽略：< 1 px³ 的碎 cube（眼、扣、耳饰）——**装饰全给碰撞体会把被子戳成马蜂窝，比穿模更难看**

### 5.3 XPBD 解算

```
每子步 dt_s = 1 / (60 × SUBSTEPS)
v ← (x - x_prev) × (1 - damping)
p ← x + v × dt_s + g × dt_s²        // g = (0, -9.8, 0) 格/s²
对每条约束（结构/剪切/弯曲）：
    C = |x_i - x_j| - L0
    Δλ = (-C - α̃ λ) / (w_i + w_j + α̃)
    α̃ = compliance / dt_s²
    x_i += w_i Δλ n ;  x_j -= w_j Δλ n
碰撞：见 5.4
x_prev ← x
```
建议初值（M7 调参起点，非最终值）：

| 参数 | 初值 | 说明 |
|---|---|---|
| `SUBSTEPS` | 4 | 1/240 子步，兼顾稳定与开销 |
| `compliance` 结构/剪切 | 0 | 布不可拉伸 |
| `compliance` 弯曲 | 8e-4 | 太大像橡皮膜，太小像钢板；这是「自然」的主旋钮 |
| `damping` | 0.02/子步 | 抑制高频抖动（呼吸动画会激励它） |
| `maxStep` | 0.05 格 | 穿透保护，防细手臂被穿过 |
| `spacing` | 1/16 格 | 与 Minecraft 像素网格对齐，贴图不抖 |

### 5.4 碰撞响应（球-粒子）

```
最近点 c = closestPointOnSegment(p, a, b)
d = |p - c|；若 d < r：
    n = (p - c) / d（d ≈ 0 时用上方向兜底）
    p ← c + n × r
    切向速度 v_t = v - n(v·n)
    v ← v - v_t × (1 - friction)     // friction：床 0.60 / 身体 0.45
```
- 每个子步结束后再做 1 次「穿透修正」，保证零穿模
- 相邻帧位移超过 `maxStep` 时按比例截断，不瞬移

### 5.5 床碰撞

- 取 `BlockMaidBed` 的 `getShape` 与 `BedGeometry.bounds()` 的**较大者**作为床面高度（现有 Q5 修好后才有意义）
- 床按「一个薄 AABB + 两块侧沿 AABB」投放，两侧下摆才能真的垂下去
- 床面高度**不得硬编码**：一部分模型睡姿悬浮（尾巴/裙摆把身体垫高），要取「她自身最低碰撞点 − 0.02」与床面取大

### 5.6 姿态无关的初始化（R4 的核心）

上床那一刻（状态 `IDLE → SETTLING`）执行一次：

1. **网格朝向**：以**床的朝向**为主轴（`along` / `across`，沿用 `QuiltRenderer.findBed` 已有的算法），不按身体主轴——床不会动，身体会
2. **覆盖范围**：`uStart/uEnd` 取「床纵向范围」与「她碰撞体投影范围 + 0.15 余量」的并集；被子必须比身体大（多余布料才能堆出褶皱）
3. **网格尺寸**：宽 1.10 格（18 列）× 长 2.05 格（33 行），约 594 粒子；布间距 1/16
4. **初始高度**：整片平铺在 (床面 + 最高碰撞点 + 0.03) 的平面上，四角不钉
5. **预松弛**：离线跑 240 步（约 1 秒模拟）再显示；分 4 帧摊开算（每帧 60 步），避免卡顿
6. **回退**：任何一步拿不到碰撞体 → 走现有解析外壳，日志 `[sleep_well] quilt physics fallback: <原因>`

### 5.7 姿态指纹与软过渡

```
fingerprint = hash(modelId, 装备槽, 关键骨矩阵（每 8 个 bone 取一个）, 动画状态)
```
- 指纹变化 → `SIMULATING → FADING`：碰撞体半径与位置在 0.4~0.6 秒内 lerp 至新值，**不清空粒子**
- 醒来（`isSleeping() == false`）→ `FADING → IDLE`，粒子在 0.5 秒内淡出，随后释放缓冲区
- 世界卸载/距离超限 → 直接丢弃缓冲区，重进时重新预松弛（**不写 NBT**）

### 5.8 渲染

- 网格 → 每格 2 三角形；法线用相邻粒子叉积（否则凹陷完全看不出来）
- `RenderType.entityCutoutNoCull`（沿用现状，双面），光照取**床**的光照值而非女仆眼睛位置
- 顶点色带一点「凹陷压深」（现有 `shades` 机制可复用）：越接近床面越暗 0.72→1.0
- 侧面/脚端不再画垂直墙，由布料自然形成褶皱；**下摆由布料重力自然垂下**，不再用 `HEM_DROP` 硬拉
- `endBatch` 当场刷（现状 `L342`），必须在 level 渲染结束前

### 5.9 帧内时序

| 顺序 | 事件 | 动作 |
|---|---|---|
| 1 | `RenderLivingEvent.Post`（每个睡着女仆） | `ColliderCapture` 写入本帧碰撞体 + 矩阵，更新指纹 |
| 2 | `RenderLevelStageEvent.AFTER_ENTITIES` | 对每个 `SIMULATING` 的被子：取累加时间，跑整数个 60 Hz 步（最多 3 步，防时间螺旋）；然后渲染 |
| 3 | 帧末 | 空地：`FADING` 计时、`IDLE` 释放缓冲区 |

- 布料步进**与帧率解耦**：`accumulator += min(frameDt, 0.1)`，稳态下与 60/144 FPS 无关
- 服务端：**零代码**

### 5.10 性能预算

| 项 | 数量 | 每帧运算 |
|---|---|---|
| 粒子 | 594 | — |
| 约束 | 结构 2 + 剪切 2 + 弯曲 2 ≈ 6/粒子 | 594 × 6 × 4 子步 ≈ 14k |
| 碰撞 | 25 胶囊 + 2~3 AABB | 594 × 28 × 4 ≈ 66k |
| 合计 | — | **≈ 8 万次约束判定/帧**，纯 `float[]` 无对象分配，目标 **< 0.6 ms** |

LOD 与上限：

| 情形 | 策略 |
|---|---|
| 距离 > 32 格 | 每 4 帧解算 1 次（视觉上静止） |
| 距离 > 48 格 / 不在视野 | 暂停解算、冻结 |
| 同屏多被子 | 只模拟最近 **2** 条，其余冻结（可配 `maxActiveQuilts`） |
| GUI 打开 / 暂停 | 停累加器 |

---

## 六、配置（`config/sleep-well-client.toml` → `[quilt]`）

| 键 | 默认 | 范围 | 说明 |
|---|---|---|---|
| `quiltPhysicsEnabled` | true | — | 物理被子总开关；关掉退回解析外壳 |
| `quiltGridSpacing` | 0.0625 | 0.04..0.12 | 布间距（格），越小越精细越贵 |
| `quiltSubsteps` | 4 | 1..8 | 子步数 |
| `quiltBendCompliance` | 0.0008 | 1e-4..5e-3 | 弯曲柔度：调「折痕多少」 |
| `quiltDamping` | 0.02 | 0..0.1 | 阻尼：调「抖不抖」 |
| `quiltFrictionBody` | 0.45 | 0..1 | 与身体摩擦：调「滑不滑」 |
| `quiltFrictionBed` | 0.60 | 0..1 | 与床摩擦 |
| `maxActiveQuilts` | 2 | 1..8 | 同时模拟条数 |
| `quiltSimDistance` | 48 | 8..96 | 超过即冻结 |
| `quiltDebugWireframe` | false | — | F3 线框（胶囊/床/网格/法线） |

写中文/英文双语注释，读不到配置时全部用默认值（沿用现有 `SleepWellConfig` 的写法）。

---

## 七、里程碑与验收

| # | 内容 | 产出 | 验收标准 |
|---|---|---|---|
| **M0** | **探针**：确认 gecko 的「动画后 bone 变换」从哪读、cube 角点怎么取 | `client/quilt/ColliderProbe.java`（`-Dsleepwell.quiltProbe=true`，每个模型打一次日志） | ✅ **数据结构已静态查清**（见第十一节），探针已编入并可跑；仍待游戏内确认「世界矩阵从哪来」 |
| **M1** | 碰撞体采集（两条路径）+ 可见性过滤 + 每帧刷新 | `client/quilt/collider/*`（已交付，见 11.4） | `quiltWireframe` 打开：线框严丝合缝套住躺姿女仆；穿甲后藏起来的手不再有线框；换模型不错位 |
| **M2** | 胶囊化 + 硬/软分级 + 体积阈值 | `QuiltCollider` | 尾巴/辫子只产生大球，不产生碎胶囊；腿/手/躯干各 ≤ 2 胶囊 |
| **M3** | XPBD 核心（**不依赖 MC**）+ 单测 | `cloth/*` | 单测：悬垂收敛、约束不爆炸、子步 dt 无关性（1/60 与 1/240 结果接近） |
| **M4** | 床碰撞 + 世界空间布置 + 预松弛 | `ClothState` | 被子第一帧就自然搭在床与身体上，无抽搐；床沿用 `FALLBACK_TOP` 时不歪 |
| **M5** | 姿态无关初始化（床轴向、最低点、范围并集） | `initLayout` | 5 个睡姿差别大的模型各盖一次，被子不悬空、不埋人、不露出脚 |
| **M6** | 渲染改造（网格/法线/光照/双面/贴图平铺） | `QuiltRenderer` | 凹陷在视觉上可见（有明暗）；侧面自然下垂；不再有垂直封墙 |
| **M7** | 状态机 + 指纹 + 软过渡 | `ClothState` 全流程 | 上床 0.5 秒内落定；醒来被子不炸；换装/切动画不撕 |
| **M8** | 参数调优 + F3 调试面板（三旋钮） | `QuiltDebugPanel` | 主人主观验收「凹陷/下垂自然」；个别模型可手动补正 |
| **M9** | LOD / 多被子 / 性能 | — | 4 个睡着女仆同屏，被子相关耗时 < 1 ms；F3 帧时间无明显抬升 |
| **M10** | 收尾：双路径兼容、配置项、`版本改动.md`、README | — | `gradlew.bat unitTest` 全绿；`gradlew.bat build` 通过；进游戏按 R1~R5 全过 |

**纪律**（工作区规范）：只在 `exp/maid-settings-gui` 提交；不 push；不改 `mod_version`；可感知改动同回合写 `版本改动.md`（新增内容/Bug 修复，中英各一句）；M10 后把本计划移入 `docs/归档/2026-11-05-physical-quilt.md`

---

## 八、风险清单

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| gecko 拿不到动画后矩阵（M0 失败） | 中 | 高 | 退路 1：读 `AnimatedGeoBone` 的 snapshot 叠加 pivot 链；退路 2：仅在 bedrock 模型上启用物理，其余走解析外壳并写一次性日志 |
| 反射脆弱（TLM 小版本改名） | 中 | 中 | 反射结果缓存 + 失败降级 + 版本日志；绝不在反射里抛异常到渲染线程 |
| 被子穿模（粒子隧穿） | 中 | 中 | `maxStep` 钳制 + 细胶囊半径下限 + 子步内二次修正 |
| 布料抖动（呼吸/尾巴持续动画激励） | 高 | 中 | 阻尼 + 碰撞切向摩擦 + 对慢速骨头做 0.1 秒低通 |
| 睡姿悬浮/陷床（模型差异） | 中 | 中 | 最低点反推床面 + F3 三旋钮手动补正 |
| 多被子同屏掉帧 | 中 | 中 | `maxActiveQuilts` + 距离 LOD + 冻结 |
| 与光影/其他渲染模组抢 `entityCutoutNoCull` | 低 | 低 | 沿用现状 RenderType；必要时自定义 RenderType |
| 玩家在床上放方块/床被拆 | 低 | 中 | 每帧校验床状态，失效即淡出 |

---

## 九、不做

- 布料自碰撞（O(n²)，纯亏）
- 布料与布料互碰、撕裂、风场
- 粒子位置网络同步、服务端解算
- 粒子位置写 NBT
- 为单个模型写姿态特例（一律靠 M5 的通用初始化 + M8 的手动旋钮）
- 玩家「拉被子」手势动画（另立计划）

---

## 十、待主人拍板

1. **被子是纯渲染还是物品**？本计划按**纯渲染**设计（零同步、零存档）；要能捡起/放箱子/掉地上，则需另加 `Entity` 外壳，工作量 +40%
2. **玩家身体算不算碰撞体**？（伸进被子会凹——几乎零成本，建议要）
3. **同屏上限 2 条**是否可接受？
4. **降级策略**是否保留现有解析外壳（建议保留：读不到模型也不至于「被子消失」）

---

## 附录 A · 关键类名（TLM 1.5.3-forge+mc1.20.1，实测）

| 用途 | 类 |
|---|---|
| 女仆渲染入口（老模型） | `com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer`（`MobRenderer<Mob, BedrockModel<Mob>>`） |
| 女仆渲染入口（gecko） | `...client.renderer.entity.GeckoEntityMaidRenderer`（`geckolib3.geo.GeoReplacedEntityRenderer`） |
| bedrock 部件 | `com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart`（`cubes`/`children`/`xRot`/`offset*`/`xScale..`/`visible` 全 public） |
| gecko 骨头 | `...geckolib3.geo.render.built.GeoBone`（`parent/children/pivot/rotation/cubes/dontRender`） |
| gecko 方块 | `...geckolib3.geo.render.built.GeoCube`（`size` + `quads`，无 min/max） |
| 床方块 | `...block.BlockMaidBed` + `...tileentity.TileEntityMaidBed`（`getColor()`） |
| 渲染 API 事件 | `...api.event.client.RenderMaidEvent`（只有 `getMaid()/getModelData()`，**没有 PoseStack 与 buffer**，只能当钩子探测，不能作绘制入口） |

## 附录 B · 骨名样例（`foxmaid.json`，证明衣服/尾巴都在骨头树里）

```
Trousers_Left / Trousers_Right / Trousers_Left3 / Trousers_Right3   裤子
Clogs2 / Clogs3                                                     木屐
Big_bow / Dbow_left / Dbow_right                                    蝴蝶结
HairBack / HairPart_LeftBraidA1~A4 / Left_SideHairC1~2              头发、麻花辫
FoxTailA / Body_Tail2~6 / Tail                                      尾巴
BackpackLocator / LeftWaistLocator / RifleLocator / PistolLocator   挂点
```

**不在骨头树里、需单独补的**：帽子头饰（`LayerMaidBipedHead`）、手持物（`LayerMaidHeld` / `GeckoLayerMaidHeld`）、背包（`LayerMaidBackpack`）、旗帜（`LayerMaidBanner`）、背后物品（`LayerMaidBackItem`）——本计划**不覆盖**，被子会从背包/镐柄穿过去；需要覆盖则 M2 之后追加一个 layer 采集器（工作量约 +1 天）

## 附录 C · 调试

| 手段 | 内容 |
|---|---|
| `quiltDebugWireframe` | 胶囊（红=硬/蓝=软）、床 AABB（黄）、布料网格（青）、法线（白） |
| 日志前缀 | `[sleep_well] quilt ...`，一次性日志用 `PRINTS` 计数限制（沿用现状写法） |
| 命令 | `/sleepwell quiltdebug`（权限 2，客户端侧只改本地开关） |
| 单测 | `src/unitTest/java/com/sleepwell/quilt/*`：XPBD 数值稳定性、胶囊-粒子碰撞、姿态指纹 |
---

## 十一、M0 探针结论（2026-11-05）

### 11.1 静态查清的部分（可直接照此写 M1）

| 环节 | 结论 |
|---|---|
| 从女仆拿到 gecko 模型 | `EntityMaidRenderer` 字段 `geckoEntityMaidRenderer` → `getAnimatableEntity(maid)` 得 `GeckoMaidEntity` → `getGeoModel()` 返回 `ILocationModel`，实际实现是 `AnimatedGeoModel` |
| 动画后的骨头变换 | `AnimatedGeoBone` 上现成：`getName()`、`getPositionX/Y/Z()`、`getRotationX/Y/Z()`、`getScaleX/Y/Z()`、`isHidden()`、`areCubesHidden()`、`children()`、`geoBone()` —— **不需要自己插进动画管线** |
| 骨头几何 | `GeoBone#pivot()` / `rotation()` / `cubes()` / `dontRender()`；`GeoMesh` 是打包数组（`position[] dx[] dy[] dz[] cubeCount`），cube 角点可用 `position + dx/dy/dz` 直接还原，**比原计划「用 quad 顶点反算 8 角」更省**（`GeoVertex.position` 留作兜底） |
| 可见性过滤 | gecko：`isHidden()` / `areCubesHidden()` / `dontRender`；bedrock：`BedrockPart.visible` —— 两套都有，Q4 可解 |
| bedrock 侧 | `modelMap` 的 **key 才是部件名**（`BedrockPart` 自身没有 name 字段），被子要按名字分级时从 key 取 |
| 世界矩阵 | **仍是唯一未定项**：`GeoBone` / `AnimatedGeoBone` 都**不缓存**世界矩阵，矩阵在渲染递归里现算。两条备选：①`RenderLivingEvent.Post` 里 `poseStack.last().pose()` 是否仍带实体变换（探针线框一验即知）；②mixin `GeoReplacedEntityRenderer#render(T, E, float, float, PoseStack, MultiBufferSource, int)` 的 HEAD 抓栈顶 |

### 11.2 已交付的探针

- 文件：`src/main/java/com/sleepwell/client/quilt/ColliderProbe.java`
- 触发：`JAVA_TOOL_OPTIONS=-Dsleepwell.quiltProbe=true gradlew runClient`（默认关闭，一次游戏只跑一遍，按渲染器类型去重）
- 日志前缀 `[sleep_well] probe ...`，逐跳打印「拿到了什么 / 哪一跳断了」：渲染器类名 → 模型类名 → `geckoEntityMaidRenderer` → `GeckoMaidEntity` → `AnimatedGeoModel` → 前 6 根骨头 + 每 20 根骨头一行（名字/TRS/隐藏/cube 数/pivot）→ bedrock `modelMap` 汇总
- 挂载点：`QuiltRenderer.onRenderLiving`（与该模组现有采样同一处，保证拿到的是睡姿）
- 验证：`gradlew compileJava` 通过（18 秒，仅剩工程既有的 `ResourceLocation(String,String)` 过时警告，与本次改动无关）
- 生命周期：M1 落地 `collider/` 后删除本类

### 11.3 对 M1 的影响

- `GeckoColliderSource` 可以纯反射实现，**不必在本轮写 mixin**（除非 11.1 最后一行走到备选②）
- 「按名分级硬/软碰撞体」在 gecko 侧可行（`getName()`），bedrock 侧靠 `modelMap` key
- 原计划 M1 的「可见性过滤 + 每帧刷新」两项目标不变
### 11.4 M1 已交付的部分（2026-11-05 第二轮）

| 文件 | 作用 |
|---|---|
| `collider/QuiltBox.java` | 一个世界空间立方体（8 角点 + 胶囊半径 + 硬/软分级 + 骨头名） |
| `collider/ColliderSet.java` | 一帧的整套轮廓 + 包围盒 + 变化戳 |
| `collider/MaidColliderCapture.java` | 从 `RenderLivingEvent.Post` 采样：gecko 走 `geckoEntityMaidRenderer → getAnimatableEntity → getGeoModel → topLevelBones`，逐骨合成 `T(pos)·T(pivot)·R·S·T(-pivot)`，cube 用 `GeoMesh` 的 `position + dx/dy/dz` 取 8 角；bedrock 走 `modelMap`（粗略占位） |
| `collider/QuiltWireframe.java` | 12 条边画盒：红=硬碰撞（身体）、蓝=软排斥（头发/尾巴/装饰） |
| `collider/Reflect.java` | 只读反射，失败只降级不抛 |

**打开方式**：`JAVA_TOOL_OPTIONS=-Dsleepwell.quiltWireframe=true gradlew runClient`

**自检**：`MaidColliderCapture.selfCheck` 会拿 `poseStack` 的平移和她相对相机的真实位置比对，偏差 > 2 格就打一条 `collider miss matrix`——这就把「Post 时 PoseStack 是否还带实体变换」从猜测变成了运行时自证。

**尚不确定、要靠线框确认的三件事**（按可能性排序）：

1. `getRotationX/Y/Z` 是否真是弧度（按弧度实现；若线框被放大了一倍左右，就是角度）
2. 骨头复合顺序是否为 `T(pos)·T(pivot)·R·S·T(-pivot)`（geckolib 的 `renderRecursively` 是「先平移 pivot、旋转、再平移回来」，若线框整体偏移，就是 pos 与 pivot 的先后问题）
3. bedrock 路径目前每个部件只给一个 0.12 格的小盒，仅用于确认「这条路上有东西」，M2 再按 `BedrockCube` 补真几何

**验收方式**：找一只睡在女仆床上的女仆，开线框截图——红线应贴着头/躯干/四肢，蓝线贴着头发/尾巴；形状随睡姿动画（呼吸）轻微起伏。
