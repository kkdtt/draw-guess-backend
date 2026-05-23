package org.ctt.draw_guess.common; // 注意：包名可能和你的略有不同，别覆盖错了

import lombok.Data;

/**
 * 统一接口返回结果类
 */
@Data // 自动生成 get/set 方法（如果你前面还没修好 @Data，记得按 Alt+Insert 手动生成 get/set！）
public class Result<T> {

    private Integer code; // 状态码：200 成功，500 失败
    private String msg;   // 提示信息
    private T data;       // 真正的数据（用泛型 T，代表什么数据都能装）

    // ----- 下面是给程序员偷懒用的“快捷静态方法” -----

    // 1. 成功，且有数据返回时调用
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    // 2. 成功，但不需要返回数据时调用（比如删除成功）
    public static Result success() {
        return success(null);
    }

    // 3. 失败时调用（报错了，传个错误信息过来）
    public static Result error(String msg) {
        Result result = new Result<>();
        result.setCode(500);
        result.setMsg(msg);
        return result;
    }
}