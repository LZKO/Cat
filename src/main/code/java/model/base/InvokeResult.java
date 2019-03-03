package src.main.code.java.model.base; 

/* *
   * Created by LIYUNYAO on 2019/3/3
   * Time: 9:42
   * Year: 2019
   */

public class InvokeResult<T> {
    private T data;
    private boolean hasErrors = false;
    private String errorMsg = "";

    private InvokeResult() {}

    private InvokeResult(T data) {
        this.data = data;
    }

    private InvokeResult(String errorMsg) {
        this.errorMsg = errorMsg;
        this.hasErrors = true;
    }

    public T getData() {
        return data;
    }

    public InvokeResult<T> data(T t) {
        this.data = t;
        return this;
    }

    public boolean hasErrors() {
        return hasErrors;
    }

    public String errorMsg() {
        return errorMsg;
    }

    public boolean successful() {
        return !hasErrors;
    }

    public boolean isHasErrors() {
        return hasErrors;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public static <T> InvokeResult<T> failure(String errorMsg) {
        return new InvokeResult<T>(errorMsg);
    }
    public static InvokeResult<Void> voidInvokeResult() {
        return new InvokeResult<Void>();
    }
}
