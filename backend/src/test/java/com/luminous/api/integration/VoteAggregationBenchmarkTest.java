package com.luminous.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.mq.VoteMessage;
import com.luminous.api.vote.VoteCommandService;
import com.luminous.api.vote.VoteTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;

/** 独立数据库上的持续对照实验，不把 H2 加锁耗时当作 MySQL 引擎锁等待或生产吞吐。 */
@EnabledIfSystemProperty(named="luminous.aggregation-benchmark.enabled", matches="true")
class VoteAggregationBenchmarkTest extends AbstractVoteIntegrationTest {
    @Autowired VoteCommandService commands;
    @Autowired VoteTransactionService transactions;
    @Autowired ObjectMapper mapper;
    private final List<Long> benchmarkVoters = new ArrayList<>();

    @Test void sustainedSingleAndAggregateComparisonPreservesFinalState() throws Exception {
        int seconds=Integer.getInteger("luminous.aggregation-benchmark.seconds",15);
        if(seconds<10 || seconds>120) { throw new IllegalArgumentException("持续压测时长应为十至一百二十秒"); }
        seedVoters();
        // 两种路径分别预热，再使用 A、B、B、A 顺序降低首次编译及运行顺序的影响。
        runRound("warmup-single",1,10);runRound("warmup-batch",32,10);
        List<Map<String,Object>> rounds=new ArrayList<>();
        for(int batch:List.of(1,32,32,1)) { rounds.add(runRound("measured-"+(rounds.size()+1),batch,seconds)); }
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("generatedAt",Instant.now().toString());report.put("javaVersion",System.getProperty("java.version"));
        report.put("database","H2 内存库，MySQL 兼容模式");report.put("hotPosts",1);report.put("voters",128);
        report.put("producers",4);report.put("consumers",4);report.put("queueCapacity",4096);
        report.put("duplicateEvery",10);report.put("measuredSecondsPerRound",seconds);
        report.put("scope","真实投票接收和消费事务；进程内有界队列；不包含 HTTP、Redis 投影或 Broker 网络");
        report.put("lockMetric","SELECT FOR UPDATE 获取耗时，包含 SQL 执行与等待；不等于 MySQL 引擎锁等待");
        report.put("rounds",rounds);
        Path path=Path.of("target","vote-aggregation-benchmark.json");Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),report);
    }

    private void seedVoters() {
        String password=jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,author.id());
        for(int i=0;i<128;i++) { long id=SnowflakeIdGenerator.getNextId();userIds.add(id);benchmarkVoters.add(id); }
        jdbcTemplate.batchUpdate("INSERT INTO users(id,username,display_name,password_hash,join_date) VALUES(?,?,?,?,?)",benchmarkVoters,128,
                (statement,id)->{statement.setLong(1,id);statement.setString(2,"E2E_TEST_aggregate_"+id);
                    statement.setString(3,"[E2E_TEST] 聚合压测");statement.setString(4,password);statement.setString(5,"2026-10-05");});
    }

    private Map<String,Object> runRound(String name,int batchSize,int seconds) throws Exception {
        long postId=createPost(true);
        BlockingQueue<VoteMessage> queue=new LinkedBlockingQueue<>(4096);
        AtomicBoolean accepting=new AtomicBoolean(true),producing=new AtomicBoolean(true);
        AtomicLong accepted=new AtomicLong(),delivered=new AtomicLong(),scoreUpdates=new AtomicLong(),batches=new AtomicLong();
        ConcurrentLinkedQueue<Long> lockTimes=new ConcurrentLinkedQueue<>();
        ExecutorService executor=Executors.newFixedThreadPool(8);
        List<Future<?>> producers=new ArrayList<>(),consumers=new ArrayList<>();
        CountDownLatch start=new CountDownLatch(1);
        long began=System.nanoTime();
        try {
            for(int worker=0;worker<4;worker++) {
                final int partition=worker;
                producers.add(executor.submit(()->{
                    await(start);long operation=0;
                    while(accepting.get() && !Thread.currentThread().isInterrupted()) {
                        long user=benchmarkVoters.get(partition*32+(int)(operation%32));
                        int value=(int)((operation/32)%3)-1;
                        VoteMessage message=commands.accept(postId,user,value).message();
                        accepted.incrementAndGet();enqueue(queue,message);
                        if(++operation%10==0) { enqueue(queue,message); }
                    }
                }));
                consumers.add(executor.submit(()->{
                    await(start);
                    while(producing.get() || !queue.isEmpty()) {
                        try {
                            VoteMessage first=queue.poll(100,TimeUnit.MILLISECONDS);if(first==null) { continue; }
                            List<VoteMessage> batch=new ArrayList<>();batch.add(first);queue.drainTo(batch,batchSize-1);
                            // 逆序消费偶数批次，持续覆盖乱序及重复投递；版本判断应保证最终状态一致。
                            if(batches.get()%2==0) { Collections.reverse(batch); }
                            var result=transactions.processBatch(batch);
                            lockTimes.add(result.lockAcquisitionNanos());scoreUpdates.addAndGet(result.scoreUpdates());
                            batches.incrementAndGet();delivered.addAndGet(batch.size());
                        } catch(InterruptedException ex) { Thread.currentThread().interrupt();return; }
                    }
                }));
            }
            began=System.nanoTime();start.countDown();
            List<Map<String,Object>> samples=new ArrayList<>();long maxPending=0;
            for(int second=1;second<=seconds;second++) {
                TimeUnit.SECONDS.sleep(1);
                for(Future<?> future:consumers) { if(future.isDone()) { future.get();throw new IllegalStateException("消费线程提前结束"); } }
                for(Future<?> future:producers) { if(future.isDone()) { future.get();throw new IllegalStateException("生产线程提前结束"); } }
                long pending=count(postId,"status<>'CONSUMED'");maxPending=Math.max(maxPending,pending);
                samples.add(Map.of("second",second,"accepted",accepted.get(),"delivered",delivered.get(),"pending",pending,"queueDepth",queue.size()));
            }
            accepting.set(false);
            for(Future<?> future:producers) { future.get(30,TimeUnit.SECONDS); }
            double measured=(System.nanoTime()-began)/1_000_000_000.0;
            // 停止生产后用同一查询读取完成与待消费数，避免两次查询间继续消费造成水位不匹配。
            var watermarks=jdbcTemplate.queryForMap("SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN status='CONSUMED' THEN 1 ELSE 0 END),0) AS consumed FROM reliable_events WHERE post_id=?",postId);
            long completedAtStop=((Number)watermarks.get("consumed")).longValue();
            long pendingAtStop=((Number)watermarks.get("total")).longValue()-completedAtStop;
            assertThat(completedAtStop+pendingAtStop).isEqualTo(accepted.get());
            long updatesAtStop=scoreUpdates.get(),batchesAtStop=batches.get();
            long drainBegan=System.nanoTime();producing.set(false);
            for(Future<?> future:consumers) { future.get(30,TimeUnit.SECONDS); }
            double drainSeconds=(System.nanoTime()-drainBegan)/1_000_000_000.0;
            assertThat(queue).isEmpty();assertThat(count(postId,"status<>'CONSUMED'")).isZero();
            assertThat(count(postId,"status='CONSUMED'")).isEqualTo(accepted.get());
            long markers=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM consumed_events WHERE event_id IN (SELECT event_id FROM reliable_events WHERE post_id=?)",Long.class,postId);
            assertThat(markers).isEqualTo(accepted.get());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vote_states WHERE post_id=? AND (desired_value<>applied_value OR accepted_version<>applied_version)",Long.class,postId)).isZero();
            int score=jdbcTemplate.queryForObject("SELECT votes FROM posts WHERE id=?",Integer.class,postId);
            assertThat(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(value),0) FROM post_votes WHERE post_id=?",Integer.class,postId)).isEqualTo(score);
            assertThat(jdbcTemplate.queryForObject("SELECT accepted_total FROM post_vote_clocks WHERE post_id=?",Integer.class,postId)).isEqualTo(score);
            assertThat(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(desired_value),0) FROM vote_states WHERE post_id=?",Integer.class,postId)).isEqualTo(score);
            List<Long> sorted=lockTimes.stream().sorted().toList();
            Map<String,Object> result=new LinkedHashMap<>();result.put("name",name);result.put("batchSize",batchSize);
            result.put("measurementSeconds",measured);result.put("accepted",accepted.get());result.put("acceptedPerSecond",accepted.get()/measured);
            result.put("uniqueConsumedAtStop",completedAtStop);result.put("uniqueConsumedPerSecond",completedAtStop/measured);
            result.put("scoreUpdatesAtStop",updatesAtStop);result.put("batchesAtStop",batchesAtStop);
            result.put("deliveredIncludingDuplicates",delivered.get());
            result.put("actualAverageBatchSize",delivered.get()/(double)batches.get());result.put("maxPendingSampled",maxPending);
            result.put("pendingAtStop",pendingAtStop);result.put("drainSeconds",drainSeconds);
            result.put("lockP50Milliseconds",percentile(sorted,0.50));result.put("lockP95Milliseconds",percentile(sorted,0.95));
            result.put("lockMaxMilliseconds",percentile(sorted,1.0));result.put("finalScore",score);
            result.put("correctness","消费记录、事件状态、业务版本、投票记录和总分全部一致");result.put("samples",samples);
            System.out.println("[E2E_TEST] 聚合对照 "+name+" batch="+batchSize+" accept/s="+Math.round(accepted.get()/measured)+" consume/s="+Math.round(completedAtStop/measured));
            return result;
        } finally { accepting.set(false);producing.set(false);start.countDown();executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS); }
    }

    private long count(long postId,String predicate) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reliable_events WHERE post_id=? AND "+predicate,Long.class,postId);
    }
    private static double percentile(List<Long> values,double quantile) {
        return values.get(Math.max(0,(int)Math.ceil(values.size()*quantile)-1))/1_000_000.0;
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException("压测启动中断",ex); }
    }
    private static void enqueue(BlockingQueue<VoteMessage> queue,VoteMessage message) {
        try { queue.put(message); } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException("压测投递中断",ex); }
    }
}
