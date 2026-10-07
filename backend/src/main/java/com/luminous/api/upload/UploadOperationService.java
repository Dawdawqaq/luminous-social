package com.luminous.api.upload;

import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.storage.StorageProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

/** 文件写入前独立提交意图，业务提交同时确认，失败删除记录仍可被调度器重试。 */
@Service
public class UploadOperationService {
    private final JdbcTemplate jdbc;
    private final StorageProvider storage;
    public UploadOperationService(JdbcTemplate jdbc, StorageProvider storage) { this.jdbc = jdbc; this.storage = storage; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long prepare(StorageProvider.PlannedFile planned) {
        long id = SnowflakeIdGenerator.getNextId();
        jdbc.update("INSERT INTO storage_operations(id,object_key,storage_type,status,lease_until) VALUES(?,?,?,'PENDING',?)",
                id, planned.key(), planned.storageType(), LocalDateTime.now().plusMinutes(5));
        return id;
    }
    @Transactional public void committed(long id) { jdbc.update("UPDATE storage_operations SET status='COMMITTED' WHERE id=?", id); }
    public List<Long> due() { return jdbc.queryForList("SELECT id FROM storage_operations WHERE status='PENDING' AND lease_until<=? LIMIT 100", Long.class, LocalDateTime.now()); }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanup(long id) {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("SELECT object_key,storage_type,status FROM storage_operations WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) { return; }
        var row = rows.getFirst();
        if (!"PENDING".equals(row.get("status"))) { return; }
        String key = row.get("object_key").toString();
        Long known = jdbc.queryForObject("SELECT COUNT(*) FROM uploaded_files WHERE stored_name=?", Long.class, key);
        if (known != null && known > 0) { committed(id); return; }
        try {
            if (!storage.type().equals(row.get("storage_type"))) { throw new IllegalStateException("清理对象与当前存储类型不一致"); }
            storage.delete(key);
            jdbc.update("UPDATE storage_operations SET status='DELETED',last_error=NULL WHERE id=?", id);
        } catch (Exception ex) {
            String error = ex.toString();
            jdbc.update("UPDATE storage_operations SET attempts=attempts+1,last_error=?,lease_until=? WHERE id=?",
                    error.substring(0, Math.min(error.length(), 1000)), LocalDateTime.now().plusSeconds(30), id);
        }
    }
}
