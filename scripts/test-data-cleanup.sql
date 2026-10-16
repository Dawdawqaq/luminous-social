-- 同一事务中按外键依赖顺序清理，保留未标记的开发数据。
START TRANSACTION;
SELECT id FROM posts WHERE id IN (SELECT id FROM test_posts) ORDER BY id FOR UPDATE;
-- 与 Lumi 发布共用短事务协调锁，删除进行中测试任务时保守结算已开始的外部调用。
SELECT id INTO @lumi_cleanup_gate FROM lumi_control WHERE id=1 FOR UPDATE;
CREATE TEMPORARY TABLE test_lumi_attempts AS SELECT a.* FROM lumi_attempts a JOIN lumi_tasks t ON t.id=a.task_id
WHERE t.post_id IN (SELECT id FROM test_posts) OR t.user_id IN (SELECT id FROM test_users);
INSERT INTO test_lumi_attempts SELECT a.* FROM lumi_attempts a JOIN lumi_chat_turns t ON t.id=a.task_id
WHERE t.user_id IN (SELECT id FROM test_users);
UPDATE lumi_budget_days b JOIN (
 SELECT budget_day,SUM(reserved_micros) freed,SUM(CASE WHEN status='STARTED' THEN reserved_micros ELSE 0 END) charged
 FROM test_lumi_attempts WHERE status IN ('RESERVED','STARTED') GROUP BY budget_day
) x ON x.budget_day=b.budget_day
SET b.reserved_micros=b.reserved_micros-x.freed,b.spent_micros=b.spent_micros+x.charged;
DELETE FROM lumi_attempts WHERE id IN (SELECT id FROM test_lumi_attempts);
DELETE FROM lumi_tasks WHERE post_id IN (SELECT id FROM test_posts) OR user_id IN (SELECT id FROM test_users);
DELETE FROM lumi_chat_turns WHERE user_id IN (SELECT id FROM test_users);
DELETE FROM lumi_chat_sessions WHERE user_id IN (SELECT id FROM test_users);
DELETE FROM lumi_chat_conversations WHERE user_id IN (SELECT id FROM test_users);
-- 成长流水随奖励级联清理；按用户和社区限定范围，保留其他开发用户数据。
DELETE FROM growth_rewards WHERE user_id IN (SELECT id FROM test_users) OR community_id IN (SELECT id FROM test_communities);
DELETE FROM growth_accounts WHERE user_id IN (SELECT id FROM test_users) OR community_id IN (SELECT id FROM test_communities);
DELETE FROM growth_subjects WHERE user_id IN (SELECT id FROM test_users) OR community_id IN (SELECT id FROM test_communities);
DELETE FROM growth_qualified_reads WHERE reader_id IN (SELECT id FROM test_users) OR post_id IN (SELECT id FROM test_posts);
DELETE FROM growth_read_sessions WHERE reader_id IN (SELECT id FROM test_users) OR post_id IN (SELECT id FROM test_posts);
DELETE FROM growth_daily_usage WHERE user_id IN (SELECT id FROM test_users);
DELETE FROM growth_content_fingerprints WHERE user_id IN (SELECT id FROM test_users);
DELETE FROM notifications WHERE receiver_id IN (SELECT id FROM test_users);
-- MySQL 同一语句不能重复打开临时表，按接收者和操作者拆开，仍限定本批次并处于同一事务。
DELETE FROM notification_like_receipts WHERE receiver_id IN (SELECT id FROM test_users);
DELETE FROM notification_like_receipts WHERE actor_id IN (SELECT id FROM test_users);
DELETE FROM notifications WHERE actor_id IN (SELECT id FROM test_users);
DELETE FROM notifications WHERE target_type='POST' AND target_id IN (SELECT id FROM test_posts);
DELETE FROM user_follows WHERE follower_id IN (SELECT id FROM test_users);
DELETE FROM follow_notification_windows WHERE actor_id IN (SELECT id FROM test_users);
DELETE FROM follow_notification_windows WHERE receiver_id IN (SELECT id FROM test_users);
DELETE FROM follow_actions WHERE user_id IN (SELECT id FROM test_users);
DELETE FROM user_follows WHERE following_id IN (SELECT id FROM test_users);
DELETE FROM consumed_events WHERE event_id IN (SELECT event_id FROM reliable_events WHERE post_id IN (SELECT id FROM test_posts) OR user_id IN (SELECT id FROM test_users));
DELETE FROM failed_messages WHERE event_id IN (SELECT event_id FROM reliable_events WHERE post_id IN (SELECT id FROM test_posts) OR user_id IN (SELECT id FROM test_users));
DELETE FROM reliable_events WHERE post_id IN (SELECT id FROM test_posts) OR user_id IN (SELECT id FROM test_users);
DELETE FROM comments WHERE author_id IN (SELECT id FROM test_users) OR post_id IN (SELECT id FROM test_posts);
DELETE FROM post_votes WHERE user_id IN (SELECT id FROM test_users);
DELETE FROM comment_votes WHERE user_id IN (SELECT id FROM test_users);
DELETE FROM bookmarks WHERE user_id IN (SELECT id FROM test_users);
DELETE FROM posts WHERE id IN (SELECT id FROM test_posts);
DELETE FROM community_members WHERE user_id IN (SELECT id FROM test_users);
DELETE FROM communities WHERE id IN (SELECT id FROM test_communities);
DELETE FROM storage_operations WHERE object_key IN (SELECT stored_name FROM test_files);
DELETE FROM uploaded_files WHERE id IN (SELECT id FROM test_files);
DELETE FROM users WHERE id IN (SELECT id FROM test_users);
DELETE FROM idempotent_requests WHERE LOCATE(@cleanup_marker,response_body)>0
OR (@include_benchmark=1 AND LOCATE('[BENCHMARK]',response_body)>0);
COMMIT;
