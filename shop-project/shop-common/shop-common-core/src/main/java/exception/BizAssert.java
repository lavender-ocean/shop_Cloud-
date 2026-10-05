package exception;


import result.ResultCode;

import java.util.Collection;

/**
 * 断言工具：消除 if-throw 样板，让业务代码关注正常流程。
 */
public final class BizAssert {

    private BizAssert() {
    }

    public static void isTrue(boolean expression, ResultCode resultCode) {
        if (!expression) {
            throw new BizException(resultCode);
        }
    }

    public static void isTrue(boolean expression, ResultCode resultCode, String message) {
        if (!expression) {
            throw new BizException(resultCode, message);
        }
    }

    public static <T> T notNull(T obj, ResultCode resultCode) {
        if (obj == null) {
            throw new BizException(resultCode);
        }
        return obj;
    }

    public static void notEmpty(Collection<?> collection, ResultCode resultCode) {
        if (collection == null || collection.isEmpty()) {
            throw new BizException(resultCode);
        }
    }
}