package src.main.code.java.model.base; 

/* *
   * Created by LIYUNYAO on 2019/3/3
   * Time: 10:07
   * Year: 2019
   */

public class Resonse {

    public static final int STATUS_OK = 1;
    public static final int STATUS_FAIL = 0;

    private int status = 1;
    private String msg = "success";

    private Object data;

    public Resonse() {
    }

    public Resonse(Object data) {
        super();
        this.data = data;
    }

    public Resonse(String errormsg) {
        super();
        this.status = 0;
        this.msg = errormsg;
    }

    public Resonse(int status,String msg) {
        this.status = status;
        this.msg = msg;
    }

    public Resonse(int status,String msg,Object data) {
        this.status = status;
        this.msg = msg;
        this.data = data;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public static Resonse success() {
        return new Resonse((Object)"请求处理成功");
    }

    public static Resonse success(Object data) {
        return new Resonse(data);
    }

    public static Resonse failure(String errormsg) {
        return new Resonse(errormsg);
    }

}
