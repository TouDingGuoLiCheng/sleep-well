# 计划：睡觉寻路卡死兜底传送（罗盘睡觉点）

- 日期：2026-10-30
- 分支：`exp/eat-properly`（实验分支，禁改 `mod_version`）
- 状态：待主人批准后开工
- 来源：主人反馈「睡觉阶段遇到梯子/特殊路径，寻路失败导致原地打转」，要求对被河童罗盘配过睡觉点的女仆加兜底

## 一、原版行为（已用 1.5.3 jar 反编译确认，作为设计依据）

`SchedulePos.tick`（每 40 tick）：

```
restrictTo(maid)                     // 按当前 activity 刷新据点：work/idle/sleepPos + 各自 range 配置
if (!hasRestriction() || !canBrainMoving()) return
if (distSqr(restrictCenter) > (radius + 4)^2 && !sameWithRestrictCenter(maid))
      TeleportHelper.teleportToRestrictCenter(maid)   // ±3 随机 + 向下找地面，内部重试 10 次
else  BehaviorUtils.setWalkAndLookTargetMemories(maid, restrictCenter, 0.7f, 3)
```

- `sameWithRestrictCenter` = 当前 `WALK_TARGET` 的目标正好是据点中心。
- **结论 1**：原版只在「据点圈外 +4 格」才传送，且**正在往中心走时豁免传送** → 圈内卡在梯子/栅栏/落差时永远不会被救，只会每 40 tick 重发同一走路目标 = 主人看到的打转。
- **结论 2**：`MaidBedTask(0.6f, 2)` 的「上床」只在 2 格内 `setPos` 吸附到床头，与「远距离传送」无关。
- **结论 3**：`restrictTo()` 首行是 `if (!isHomeModeEnable()) return` → 未开 home mode 时据点不跟日程走。

## 二、要做的行为（规格）

对满足**全部**条件的女仆执行一次兜底传送：

1. 服务端；实体是 `EntityMaid`；未 `isSleeping()`；不在载具（`!maid.isPassenger()` 且 `!maid.isVehicle()`）
2. 当前 activity `== Activity.REST`（`maid.getScheduleDetail()`）
3. `maid.getSchedulePos().isConfigured()` 且 `getSleepPos() != null`
4. `schedulePos.getDimension()` 与 `maid.level().dimension().location()` 相同
5. 距 sleepPos **> 3 格**（`distSqr > 9`），否则她已到床边，交给 `MaidBedTask` 吸附
6. **无进展**：每 20 tick 采样一次到 sleepPos 的距离，连续 5 次采样（100 tick = 5 s）内**最近距离没有缩小 ≥1 格** → 判为卡死（用「最佳距离不改进」而非「瞬时距离变大」，避免正常绕路误判）
7. 本次入睡会话（进入 REST 起算）已传送次数 < `maxTeleports`（默认 2）；距上次传送 ≥ 200 tick

落点（主人定：**原版安全落点优先**，自算落点兜底）：

1. 先调 **1 次** `TeleportHelper.teleportToRestrictCenter(maid)`（据点中心 ±3 随机 + 向下找地面，返回 boolean）——**只试一次**；不复用 `SchedulePos.teleport` 那层「最多 10 次重试」的包装，免得连抽 10 次随机落点把女仆甩到奇怪位置
2. 这一次返回 false（实体已死 / 客户端侧 / 找不到落点）→ 立刻改走自算 `SleepLandingSpot.find(...)`：sleepPos 本身 / `水平 ±2 随机、y+0..1`、向下最多找 4 格直到可站立面
3. 自算校验：`level.noCollision(maid, aabbAt)` + 脚位与头位 `isEmptyBlock` + `pos.y() > level.getMinBuildHeight()` + 站立面 `.isSolid()` 且非岩浆
4. 两条都失败 → 本会话放弃，只打 debug 日志，不做任何事
5. 传送后：`setDeltaMovement(0,0,0)`、`getNavigation().stop()`、清 `WALK_TARGET` / `LOOK_TARGET`，让 `MaidBedTask` 重新接管

> 注意：`teleportToRestrictCenter` 用的是 `getRestrictCenter()`。REST 阶段且开 home mode 时 `restrictTo()` 已把它刷成 sleepPos（两者相等）；home mode 关闭时会落在旧据点上。按主人意愿仍以原版落点为准，只在 debug 日志里标出「center ≠ sleepPos」便于排查

配置（`EatProperlyConfig`，新的 `builder.push("sleepFallback")` 段，读取时 `.get()`，无需热重载钩子）：

| 键 | 类型 | 默认 | 范围 |
|---|---|---|---|
| `sleepFallbackEnabled` | boolean | `true` | — |
| `sleepFallbackSeconds` | int | `5` | 2..60 |
| `sleepFallbackMaxTeleports` | int | `2` | 0..5 |

## 三、不做什么（YAGNI，先记着）

- 不加 mixin，不动 TLM 内部类
- 不复用 `SchedulePos.teleport`（它会把 `teleportToRestrictCenter` 连打最多 10 次）；本模组只调 1 次原版落点，失败即转自算
- 不做「直接 `startSleeping` + `setPos` 上床」模式（若主人想要，作为 v2 单开一版）
- 不覆盖 work/idle 阶段卡死（同机制，但不在本次范围）
- 不改 `mod_version`

## 四、文件结构

新增 `src/main/java/com/touhoumaid/eatproperly/sleep/`：

| 文件 | 职责 |
|---|---|
| `SleepFallbackPolicy.java` | 纯判定：常量 + `shouldTeleport(...)`，无 MC 类型 |
| `SleepFallbackTracker.java` | 每只女仆的会话状态：最佳距离、无进展采样数、本会话次数、冷却；纯 Java |
| `SleepLandingSpot.java` | 兜底落点：原版 `TeleportHelper` 失败后，围绕 sleepPos 搜索 + 安全校验（吃 `ServerLevel` + `EntityMaid` + `BlockPos`） |
| `SleepFallbackHandler.java` | `@Mod.EventBusSubscriber` + `LivingEvent.LivingTickEvent`：解析罗盘点、调 tracker、执行传送 |

新增测试 `src/unitTest/java/com/touhoumaid/eatproperly/sleep/`：

- `SleepFallbackPolicyTest.java`
- `SleepFallbackTrackerTest.java`

修改：

- `config/EatProperlyConfig.java`：字段声明 + `push("sleepFallback")` 段（放在 `satiety` 之后、`snackRaid` 之前或末尾均可，遵循现有排版）
- `版本改动.md`：新增内容 中英各一句

## 五、任务

### T0 前置（需主人确认）

- [ ] **仍待主人拍板**：工作区在 `exp/eat-properly` 有未提交改动（`EatProperlyMod` / `ClientGuiEvents` / `ConfigReloadListener` / `EatProperlyConfig` / `MilestoneRegistry` 等 M 状态）→ 先确认归属：先提交/暂存，还是允许本次在其上继续改
- [ ] 确认「睡觉段」判定用 `Activity.REST`（TLM 白班 22–06、夜班 06–14）

### T1 纯判定 `SleepFallbackPolicy`（TDD）✅ 已完成

- 常量：`SAMPLE_INTERVAL_TICKS = 20`、`SAMPLES_PER_WINDOW = 5`、`MIN_DIST_SQR = 9.0`、`MIN_IMPROVEMENT = 1.0`、`COOLDOWN_TICKS = 200`
- 签名（示例）：`public static boolean shouldTeleport(boolean enabled, boolean resting, boolean sleeping, boolean inVehicle, boolean configured, boolean sameDimension, BlockPosFree free, double distSqr, double bestDistSqrThisWindow, int stallSamples, int teleportsThisNap, int ticksSinceLastTeleport, int maxTeleports)`
- 先写测试：正常走路不触发、距离 <3 格不触发、关闭开关、超出次数、冷却未到、维度不同、`configured=false`、`sleepPos=null`
- 验收：`gradlew test` 中该类全绿

### T2 会话状态 `SleepFallbackTracker`（TDD）✅ 已完成

- 每只女仆一个实例（handler 里 `Map<Integer, SleepFallbackTracker>`，女仆卸载/会话结束清理）
- 采样节流：`onTick(distSqr)` 内部按 20 tick 取一次样；记录 `bestDistSqr`、`stallSamples`
- 会话重置：activity 离开 REST 或开始 `isSleeping()` → 清零（次数、冷却、采样）
- 测试：5 s 无改进 → stallSamples 到 5；距离每窗口改进 2 格 → 不累计；传送后次数 +1、冷却生效；会话重置清空

### T3 落点（原版优先 + 自算兜底）✅ 已完成

- `static boolean teleportVanillaOnce(EntityMaid maid)` → 直接 `TeleportHelper.teleportToRestrictCenter(maid)`，**单次调用**，不包 `SchedulePos.teleport` 的 10 次重试循环
- `static Optional<Vec3> find(ServerLevel level, EntityMaid maid, BlockPos sleepPos)`（仅当上一步返回 false 时调用）
- 自算顺序：sleepPos → `水平 ±2 随机、y+0..1` 若干候选 → 向下最多 4 格找支撑面；每个候选做碰撞/空位/支撑/非岩浆校验，返回中心点 `+0.5`
- 无单测（依赖世界状态）；常量集中，便于实机调

### T4 接线 `SleepFallbackHandler` ✅ 已完成

- `@Mod.EventBusSubscriber(modid = EatProperlyMod.MOD_ID)`、`LivingEvent.LivingTickEvent`、先 `isClientSide` 短路再 `instanceof EntityMaid`
- 解析：`getScheduleDetail()`、`getSchedulePos()` 三项、维度比较、`distSqr`
- 执行：**只调一次** `TeleportHelper.teleportToRestrictCenter(maid)`（原版安全落点）→ 成功则清导航与记忆收工；返回 false 才 `SleepLandingSpot.find(...)` + `maid.teleportTo`，仍失败则放弃
- 日志：`EatProperlyMod.LOGGER.debug` 打「卡死判定 + 落点结果 + 传送次数」，便于实机排错

### T5 配置项 ✅ 已完成

- 三个字段 + `builder.push("sleepFallback")` 段，注释写明用途与默认值
- 验收：`gradlew compileJava` 通过；`runClient` 里配置文件生成出该段

### T6 收尾

- `版本改动.md`：新增内容（中英各一句，写结果不写实现）
- `gradlew build` → reobf 后的 jar 覆盖进测试实例 `D:\Game\MineCraft\.minecraft\versions\Better MC [FORGE] BMC4\mods\`
- 实机验收单（见下）跑完，把结果补回本文件

## 六、实机验收（一次性跑完）

| # | 场景 | 期望 |
|---|---|---|
| A1 | 床在 2 层，女仆在 1 层被围住/只有梯子 | 5 s 左右瞬移到据点中心附近（原版落点），随后自行上床；不打转 |
| A1b | 同上但关掉 home mode（据点≠罗盘点） | 仍瞬移（原版落点优先），debug 日志标出 center ≠ sleepPos |
| A2 | 正常 20 格直线走过去 | **不**瞬移（关键防误伤） |
| A3 | 距 sleepPos 2 格但床头被占 | 不瞬移（交给原版）、不刷屏 |
| A4 | 女仆正在睡觉 / 骑载具 | 不瞬移 |
| A5 | 关开关 `sleepFallbackEnabled=false` | 完全恢复原版行为 |
| A6 | 次日起床 → 当天再睡 | 会话次数已重置，能再次兜底 |
| A7 | sleepPos 在另一维度 | 不瞬移、仅 debug 日志 |

## 七、风险与回滚

- 误伤风险：正常长距离行走被瞬移 → 靠「最佳距离 5 s 无改进 + >3 格」双重门槛压制；A2 必测
- 传送落点不安全 → `SleepLandingSpot` 全套校验 + `TeleportHelper` 兜底；再不行放弃
- 循环瞬移 → 每会话次数上限 + 200 tick 冷却
- 回滚：开关置 false 即等于原版；代码上四个文件可整包删除，仅剩一行配置项