package test; 

/* *
   * Created by Allen on 2020/9/4
   * Time: 11:12
   * Year: 2020
   */

import java.util.LinkedList;
import java.util.Stack;

public class MinStack {

    private Stack<Integer> stack1 = null;
    private Stack<Integer> stackMin = null;

    public MinStack() {
        stack1 = new Stack<>();
        stackMin = new Stack<>();
    }

    public Integer pop() {
        Integer val = stack1.pop();
        stackMin.pop();
        return  val;
    }

    public boolean push(Integer val) {
        stack1.push(val);
        if(stackMin.isEmpty()) {
            stackMin.push(val);
        } else {
            Integer min = Math.min(val, stackMin.peek());
            stackMin.push(min);
        }

        return true;
    }

    public Integer getMin() {
        if(stackMin.isEmpty()) return null;
        Integer val = stackMin.peek();
        return val;
    }
}
