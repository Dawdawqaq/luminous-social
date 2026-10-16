# 等级星星素材

六款经用户确认的透明 PNG 原图复制自 `assets/brand/level-stars-v3/`，不裁切或重绘。
生成方式、输入原图和提示词见 [素材来源](../../../../assets/brand/level-stars-v3/README.md)。

| 等级 | 文件 |
| --- | --- |
| 1—4 | star-stage-01.png |
| 5—9 | star-stage-02.png |
| 10—12 | star-stage-03.png |
| 13—15 | star-stage-04.png |
| 16—17 | star-stage-05.png |
| 18 | star-stage-06.png |

分段代码的唯一来源为 `src/components/ui/levelStars.ts`；两个等级维度复用造型，但分别传入真实等级。
`LevelBadge` 已准备，业务页面等待真实成长接口，不伪造默认等级。
