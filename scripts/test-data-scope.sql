-- 仅使用显式标记确定测试数据，不根据普通用户名字猜测。
CREATE TEMPORARY TABLE test_users AS SELECT id FROM users
WHERE LOCATE(@cleanup_marker, display_name)>0 OR (@include_benchmark=1 AND LOCATE('[BENCHMARK]',display_name)>0);
CREATE TEMPORARY TABLE test_communities AS SELECT id, slug FROM communities
WHERE LOCATE(@cleanup_marker,name)>0 OR owner_id IN (SELECT id FROM test_users)
OR (@include_benchmark=1 AND LOCATE('[BENCHMARK]',name)>0);
CREATE TEMPORARY TABLE test_posts AS SELECT id FROM posts
WHERE author_id IN (SELECT id FROM test_users) OR community_id IN (SELECT id FROM test_communities)
OR LOCATE(@cleanup_marker,title)>0 OR (@include_benchmark=1 AND LOCATE('[BENCHMARK]',title)>0);
CREATE TEMPORARY TABLE test_files AS SELECT id,stored_name,COALESCE(storage_type,'local') AS storage_type FROM uploaded_files
WHERE user_id IN (SELECT id FROM test_users);
SELECT 'USER', CAST(id AS CHAR) FROM test_users;
SELECT 'POST', CAST(id AS CHAR) FROM test_posts;
SELECT 'COMMUNITY', slug FROM test_communities;
SELECT 'COMMUNITY_ID', CAST(id AS CHAR) FROM test_communities;
SELECT 'FILE', storage_type, stored_name FROM test_files;
