# 睡个好觉 / Sleep Well

让女仆真的能睡到床上。

## 这解决什么问题

原版《车万女仆》的 `SchedulePos.tick` 每 40 tick 检查一次：只有当女仆距离据点中心超过
`(据点半径 + 4)` **并且** 她当前的 `WALK_TARGET` 不是"正走向据点中心"时，才会调
`TeleportHelper.teleportToRestrictCenter` 把她拉回去

于是她在据点**圈内**卡住时（梯子、栅栏、一格落差、门被关）永远拿不到传送兜底：每 40 tick 只是把
同一个走路目标重发一遍，人也原地打转。而 `MaidBedTask` 的"上床"只在 2 格内吸附，救不了远处

本模组补的就是这段空档：用河童罗盘给女仆设过睡觉点后，如果 5 秒内到睡觉点的最近距离没有任何改善，
就先试一次原版安全落点，不行再在她睡觉点周围搜索一个能站的地方，把她送过去，然后交还给原版的
上床逻辑

## 配置

`config/sleep-well-common.toml` → `[sleepFallback]`

| 键 | 默认 | 范围 | 说明 |
|---|---|---|---|
| `sleepFallbackEnabled` | `true` | — | 总开关 |
| `sleepFallbackSeconds` | `5` | 2..60 | 多久没有进展才动手 |
| `sleepFallbackMaxTeleports` | `2` | 0..5 | 每次入睡最多传送几次（0 = 关闭） |

## 行为边界

- 只在她处于睡觉时段（`Activity.REST`）、还没睡着、没骑载具、且睡觉点与她同维度时生效
- 离睡觉点 3 格内不动手（那是原版上床吸附的范围）
- 每次入睡有次数上限 + 200 tick 冷却，避免反复闪现
- 传送后会清掉当前走路/注视目标，让原版 `MaidBedTask` 重新接管

## 依赖

- Minecraft 1.20.1 + Forge 47.x
- 车万女仆《Touhou Little Maid》1.5.0+（硬依赖）

## 构建

```
gradlew.bat build        # 产物在 build/libs 与 releases/
gradlew.bat unitTest     # 纯 JVM 单测（不需要启动 Forge）
```
