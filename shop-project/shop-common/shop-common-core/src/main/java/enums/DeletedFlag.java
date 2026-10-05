package enums;

import lombok.Getter;

@Getter
public enum DeletedFlag {
    NOT_DELETED(0, "未删除"),
    DELETED(1, "已删除");

    private final int code;
    private final String desc;

    DeletedFlag(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}