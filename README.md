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

`config/sleep-well-common.toml` → `[campfireFlee]`

| 键 | 默认 | 范围 | 说明 |
|---|---|---|---|
| `campfireFleeEnabled` | `true` | — | 被篝火烫到就跑开的开关 |
| `campfireFleeRadius` | `6` | 2..12 | 安全落点的搜索半径（格） |

## 篝火跑开

原版篝火对站在里面的女仆只造成普通火焰伤害，她自己不会动，于是站在火里一直掉血

只要她脚下那一列有篝火、且这一下伤害是火焰类型，本模组就给她挑一个 3～6 格（可配）内
3×3×3 范围都没有篝火/火/岩浆/岩浆块的安全落点，用寻路走过去（不是传送），每次最多跑 5 秒

## 行为边界

- 只在她处于睡觉时段（`Activity.REST`）、还没睡着、没骑载具、且睡觉点与她同维度时生效
- 离睡觉点 3 格内不动手（那是原版上床吸附的范围）
- 每次入睡有次数上限 + 200 tick 冷却，避免反复闪现
- 传送后会清掉当前走路/注视目标，让原版 `MaidBedTask` 重新接管
- 篝火跑开只看"脚下有篝火 + 火焰伤害"：掉进岩浆、踩岩浆块、被普通的火烫到都不触发
- 跑开是走路不是传送，落点只看安全不看可达；跑不到就每 2 秒换一个目标，5 秒后放弃交还给原版

## 依赖

- Minecraft 1.20.1 + Forge 47.x
- 车万女仆《Touhou Little Maid》1.5.0+（硬依赖）

## 构建

```
gradlew.bat build        # 产物在 build/libs 与 releases/
gradlew.bat unitTest     # 纯 JVM 单测（不需要启动 Forge）
```
