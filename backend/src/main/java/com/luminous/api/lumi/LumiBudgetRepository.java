package com.luminous.api.lumi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

/** 评论助手与私有聊天共用费用账本，调用方必须持有 lumi_control 的短事务锁。 */
@Repository
public class LumiBudgetRepository {
    private final JdbcTemplate jdbc;
    public LumiBudgetRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public LumiRepository.Attempt reserve(long taskId,int sequence,LumiProperties p,int outputTokens,LocalDateTime now){
        LocalDate day=now.plusHours(8).toLocalDate();
        jdbc.update("INSERT INTO lumi_budget_days(budget_day) VALUES(?) ON DUPLICATE KEY UPDATE budget_day=VALUES(budget_day)",day);
        var budget=jdbc.queryForMap("SELECT * FROM lumi_budget_days WHERE budget_day=? FOR UPDATE",day);
        long reserved=p.cost(p.getMaxInputBytes()+1024L,outputTokens);
        if(((Number)budget.get("calls")).intValue()>=p.getDailyCalls()
                ||((Number)budget.get("reserved_micros")).longValue()+((Number)budget.get("spent_micros")).longValue()+reserved>p.getDailyBudgetMicros())return null;
        long id=com.luminous.api.common.SnowflakeIdGenerator.getNextId();
        jdbc.update("UPDATE lumi_budget_days SET calls=calls+1,reserved_micros=reserved_micros+? WHERE budget_day=?",reserved,day);
        jdbc.update("INSERT INTO lumi_attempts(id,task_id,sequence_no,budget_day,reserved_micros,status,created_at) VALUES(?,?,?,?,?,'RESERVED',?)",id,taskId,sequence,day,reserved,now);
        return new LumiRepository.Attempt(id,taskId,day,reserved);
    }
    public void settle(LumiRepository.Attempt a,Long actual,Integer input,Integer output){
        long cost=actual==null?a.reserved():actual;
        if(jdbc.update("UPDATE lumi_attempts SET status='SETTLED',charged_micros=?,input_tokens=?,output_tokens=?,finished_at=UTC_TIMESTAMP(3) WHERE id=? AND status IN ('RESERVED','STARTED')",cost,input,output,a.id())==1)
            jdbc.update("UPDATE lumi_budget_days SET reserved_micros=reserved_micros-?,spent_micros=spent_micros+? WHERE budget_day=?",a.reserved(),cost,a.day());
    }
    public void settleUnknown(long task){
        jdbc.query("SELECT id,task_id,budget_day,reserved_micros,CASE WHEN status='STARTED' THEN reserved_micros ELSE 0 END charged FROM lumi_attempts WHERE task_id=? AND status IN ('RESERVED','STARTED') FOR UPDATE",
                (r,i)->Map.entry(new LumiRepository.Attempt(r.getLong(1),r.getLong(2),r.getObject(3,LocalDate.class),r.getLong(4)),r.getLong(5)),task).forEach(a->settle(a.getKey(),a.getValue(),null,null));
    }
}
