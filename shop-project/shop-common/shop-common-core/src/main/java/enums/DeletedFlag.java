package enums;

import lombok.Getter;

@Getter
// `values()` 是编译器自动生成方法，返回全部枚举数组。
// 枚举使用@Getter不能使用@Data
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