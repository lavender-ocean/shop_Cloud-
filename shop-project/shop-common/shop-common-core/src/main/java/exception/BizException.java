package exception;

import lombok.Getter;
import result.ResultCode;

/**
 * 业务异常：可预期、需要把 message 返回给前端。
 * 系统异常（NPE 等）不走这里，交给全局兜底。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}