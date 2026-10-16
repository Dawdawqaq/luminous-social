# 六阶段星星等级标识

日期：2026-10-11。状态：设计预览，等待用户取舍；未接入页面。

本轮比较星星自身的变化。以用户批准的黑边、四角、黄色主体和蓝色右角星星为唯一图形参考；第一个阶段使用批准原图的副本，另外五个阶段由内置 image_gen 工具分别编辑生成，保持透明 PNG。

| 文件 | 示例等级 | 视觉变化 |
| --- | --- | --- |
| [star-stage-01.png](star-stage-01.png) | 1—3 | 批准的原型，简单图形 |
| [star-stage-02.png](star-stage-02.png) | 4—6 | 黄色更饱满，增加柔和高光 |
| [star-stage-03.png](star-stage-03.png) | 7—9 | 蓝色角增加细纹 |
| [star-stage-04.png](star-stage-04.png) | 10—12 | 增加一层内部轮廓 |
| [star-stage-05.png](star-stage-05.png) | 13—15 | 少量大切面的小宝石 |
| [star-stage-06.png](star-stage-06.png) | 16—18 | 宝石切面与两颗小闪星 |

完整提示词为 star-stage-02.prompt.txt 至 star-stage-06.prompt.txt。第一个阶段沿用 assets/brand/luminous-star-blue-corner-soft-preview-20261011.png，没有重新生成。

[打开完整预览](preview.html) 同时提供大图、28px / 40px 小图、同底框等级标识和深色背景的对照。阶段分组与图形复杂度均为待取舍的视觉建议，不新增业务规则。

完整提示词：[柔亮](star-stage-02.prompt.txt)、[细纹](star-stage-03.prompt.txt)、[内框](star-stage-04.prompt.txt)、[切面](star-stage-05.prompt.txt)、[宝石](star-stage-06.prompt.txt)。

六张均为 1254×1254 的透明 RGBA PNG，已检查透明通道。小尺寸对照中，前几个阶段的差异较轻，内部轮廓、切面和附加小闪星更易辨认；最终阶段方案需以用户取舍为准。

当前等级系统尚未实现。此次不替换导航图标、猫猫道具或实际页面，后续依据用户取舍再调整。
