package entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公共字段基类。
 * 刻意【不包含 version】—— 只有需要乐观锁的表（inventory / order 等）才在自己的实体里声明 version，
 *    避免给所有表强加一个可能不存在的列。
 */
@Data
public class BaseEntity implements Serializable {

    /** 主键：MP 内置雪花（ASSIGN_ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 逻辑删除：0 未删除 / 1 已删除（配合 @TableLogic 自动改查询与删除） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}