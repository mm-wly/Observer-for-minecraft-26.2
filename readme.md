# Observer 2.0.1

Observer 是 Minecraft Java Edition 26.2 的纯客户端 Fabric Mod。它通过命令检索客户端已加载的方块、实体、群系及结构候选，并持续更新结果、显示高亮和提供视角导航。服务端无需安装本 Mod

## 安装

1. 准备 Minecraft Java Edition **26.2**、Java **25**、Fabric Loader **0.19.3**、Fabric API **0.154.0+26.2**。
2. 将本目录的 `observer-2.0.1.jar` 放入该游戏实例的 `mods` 文件夹，同时安装 Fabric API。
3. 确保 `mods` 内只有一份 Observer，启动游戏并进入世界。

无需将 `source` 文件夹放入游戏。Mod 只读取客户端实际收到的数据，不获取世界种子、服务端结构起点或未加载区域；不能替代全世界结构定位器。结构识别可能把玩家建筑当作候选。

## 五分钟上手

按游戏聊天键打开输入框，依次尝试：

```mcfunction
/obs block diamond_ore
/obs structure mineshaft
/obs feature amethyst_geode
/obs entity creeper
/obs biome cherry_grove
/obs list
```

每条查询都会持续执行，直到清除；重复同一条命令不会再创建一份查询。没有结果时可走动加载更多区域，但只能查当前仍加载的部分。

`/obs list` 显示查询列表和扫描进度，点击查询名称查看当前结果。聊天列表每页 8 个目标；点击目标行立即转动视角朝向目标，并显示导航信息，不会自动行走或传送。实体导航会跟随实体。

```mcfunction
/obs page 1 2
/obs clear structure mineshaft
/obs clear
```

第一条表示查看查询 ID 为 1 的第 2 页；第二条清除指定查询；最后一条清除全部查询、导航和 Observer 添加的高亮。

## 命令与补全

| 命令 | 含义 |
|---|---|
| `/obs structure <名称>` | 结构候选，如 village、mineshaft、shipwreck |
| `/obs feature <名称>` | 地物：amethyst_geode、dungeon、lava_lake、fossil |
| `/obs block <名称>` | 精确方块类型，包含该类型的所有状态 |
| `/obs entity <名称>` | 当前客户端已接收且存活的实体类型 |
| `/obs biome <名称>` | 群系采样；一个匹配区块对应一个结果 |
| `/obs list` | 查看活动查询和扫描状态 |
| `/obs clear <类别> <名称>` | 清除指定查询 |
| `/obs clear` | 清除全部 |
| `/obs page <页码>` | 最近创建查询的指定页 |
| `/obs page <查询ID> <页码>` | 指定查询的指定页 |

输入名称后按 Tab 可补全单词。

结构支持具体变体以及 village、ocean_ruin、mineshaft、ruined_portal、shipwreck 等统一查询。其余名称用 Tab 查看。

## 结果语义

- 按玩家到导航点的三维距离排序；总数与保留结果数不同，默认保留最近 4096 个，绘制最多 512 个方框。
- 方块精确匹配类型，diamond_ore 不包含 deepslate_diamond_ore；结构与地物是启发式候选，其框和数量不等于服务端生成记录。
- 群系数量是匹配区块数，不是连续群系区域数。地下群系也可检索。
- 只有成员增加、删除或替换才自动报告；玩家移动或实体走动不会仅因距离变化反复刷屏。
- 方块使用青色框，结构/地物使用红色框，近处更粗亮、远处更淡；实体用原版发光轮廓。
- 换维度保留查询意图、重建结果；断开世界清空查询。清除 Observer 不会取消服务端给予实体的原版发光。
- 游戏提示支持简体中文与英文，跟随游戏语言；命令名和注册 ID 不翻译。

## 配置

首次运行后在游戏实例 `config/observer.json` 生成，修改后重启。默认值：

```json
{
  "scanBudgetMillis": 2.0,
  "maxSectionsPerTick": 128,
  "maxResults": 4096,
  "maxRenderBoxes": 512,
  "maxQueries": 16,
  "refreshMillis": 500
}
```

## 构建源码

安装 JDK 25 并设置 `JAVA_HOME`，进入 `source`：

```powershell
.\gradlew.bat assemble
```

Linux/macOS 可用 `sh ./gradlew assemble`。首次构建需要联网下载 Gradle、Fabric 与 Minecraft 编译依赖；本包不是离线依赖包。输出位于 `source/build/libs/observer-2.0.1.jar`。
