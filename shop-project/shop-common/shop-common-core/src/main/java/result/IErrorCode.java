package result;


/**
 * 错误码统一接口
 * 枚举与自定义码都实现它，方便 Result.fail(IErrorCode)
 */
public interface IErrorCode {

    int getCode();
    String getMessage();

}

