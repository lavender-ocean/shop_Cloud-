package handler;


import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import context.UserContext;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充：
 * - 时间：createTime / updateTime
 * - 操作人：createBy / updateBy（取当前登录用户；系统任务 / 未登录填 0）
 * - deleted：插入时默认 0
 */
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final long SYSTEM_USER_ID = 0L;

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserId();

        // strict*Fill：仅当字段存在且配置了对应 FieldFill 时才填充，字段不存在不会报错
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", Long.class, userId);
        this.strictInsertFill(metaObject, "updateBy", Long.class, userId);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", Long.class, currentUserId());
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        return userId == null ? SYSTEM_USER_ID : userId;
    }
}
