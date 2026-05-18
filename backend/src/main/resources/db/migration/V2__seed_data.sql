INSERT INTO users (id, username, display_name, password_hash, avatar_url, banner_url, bio, join_date)
VALUES
(1001, 'felix_the_maker', '飞力创作', '$2a$10$v1a7dYF5bEIrLJ9QH4rze.E0OzJt.x30gZr6Eq7VC2C5oC3m9S6eO', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=160&q=80', 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=1200&q=80', '数字产品设计师与社区建设者。专注于构建模块化 UI 系统，探索艺术与实用的平衡边界。', '2024年3月'),
(1002, 'DesignSeeker', 'Design Seeker', '$2a$10$v1a7dYF5bEIrLJ9QH4rze.E0OzJt.x30gZr6Eq7VC2C5oC3m9S6eO', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=160&q=80', NULL, '工业设计观察者。', '2024年5月'),
(1003, 'NeuroLinker', 'Neuro Linker', '$2a$10$v1a7dYF5bEIrLJ9QH4rze.E0OzJt.x30gZr6Eq7VC2C5oC3m9S6eO', NULL, NULL, '关注人机协作与未来社会。', '2024年6月');

INSERT INTO communities (id, slug, name, icon_url, banner_url, description, members_count, members_unit, rules_json, moderators_json)
VALUES
(2001, 'pixel8', 'Pixel 8 吧', 'https://images.unsplash.com/photo-1598327105666-5b89351aff97?auto=format&fit=crop&w=240&q=80', 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1200&q=80', 'Pixel 8 用户的交流空间。分享照片、系统更新、使用技巧与设备问题排查。', 142000, 'Members', JSON_ARRAY('尊重其他成员，禁止人身攻击。', '内容需与 Pixel 8 系列或移动影像相关。', '低质量求助请先阅读置顶指南。', '禁止未经许可的推广和返利链接。'), JSON_ARRAY(JSON_OBJECT('id','1','username','u/JayDev','initials','JD'), JSON_OBJECT('id','2','username','u/PixelLady','initials','PL'))),
(2002, 'futurism', 'Futurism 探索者', 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=240&q=80', 'https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&w=1200&q=80', '讨论 AI、自动化、空间计算和未来社会形态。', 86000, 'Members', JSON_ARRAY('保持开放但理性的讨论。', '引用事实请尽量附来源。', '禁止恐慌式标题党。'), JSON_ARRAY(JSON_OBJECT('id','3','username','u/ModK','initials','MK')));

INSERT INTO community_members (id, community_id, user_id)
VALUES (3001, 2001, 1001), (3002, 2002, 1001);

INSERT INTO posts (id, author_id, community_id, title, content, image_url, votes, comments_count, created_at)
VALUES
(4001, 1002, 2002, '探索模块化家具系统的未来', '我一直在研究一种可以随居住状态变化而呼吸的家具系统：磁吸连接点、可替换功能块和可持续材料是它的核心。', 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=1200&q=80', 354, 2, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(4002, 1003, 2002, '为什么未来十年属于人机协作系统', '自动化规模越大，人类判断、共情与伦理监督的价值越明显。我们不是被替代，而是在变成更高级的编排者。', NULL, 892, 1, DATE_SUB(NOW(), INTERVAL 12 HOUR)),
(4003, 1001, 2001, 'Pixel 8 Pro 的夜景模式简直绝了', '周末拍了一组夜景，动态范围和色彩稳定性都比预期好。欢迎分享你们的样张设置。', 'https://images.unsplash.com/photo-1516724562728-afc824a36e84?auto=format&fit=crop&w=1200&q=80', 432, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR));

INSERT INTO comments (id, post_id, author_id, content, votes, created_at)
VALUES
(5001, 4001, 1001, '非常赞同，模块化系统在小户型里会特别有价值。', 45, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(5002, 4001, 1003, '这些渲染图很有质感，你后续会做实体打样吗？', 12, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(5003, 4002, 1001, '“编排者”这个说法很准确，关键是把工具链设计得足够可控。', 8, DATE_SUB(NOW(), INTERVAL 45 MINUTE));
