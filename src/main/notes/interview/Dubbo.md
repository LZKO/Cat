# Dubbo

------

## 一、面试题

### 1、dubbo负载均衡是如何实现的？

[参考](<https://www.cnblogs.com/luozhiyun/p/10963116.html>)

![img](https://img2018.cnblogs.com/blog/1204119/201906/1204119-20190602153831743-365076150.png)

dubbo的负载均衡全部由AbstractLoadBalance的子类来实现

**1.1、RandomLoadBalance 随机**

在一个截面上碰撞的概率高，但调用量越大分布越均匀，而且按概率使用权重后也比较均匀，有利于动态调整提供者权重。

1. 获取invoker的数量
2. 获取第一个invoker的权重，并复制给firstWeight
3. 循环invoker集合，把它们的权重全部相加，并复制给totalWeight，如果权重不相等，那么sameWeight为false
4. 如果invoker集合的权重并不是全部相等的，那么获取一个随机数在1到totalWeight之间，赋值给offset属性
5. 循环遍历invoker集合，获取权重并与offset相减，当offset减到小于零，那么就返回这个inovker
6. 如果权重相等，那么直接在invoker集合里面取一个随机数返回

```java
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int length = invokers.size(); // Number of invokers
        boolean sameWeight = true; // Every invoker has the same weight?
        int firstWeight = getWeight(invokers.get(0), invocation);
        int totalWeight = firstWeight; // The sum of weights
        for (int i = 1; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation);
            totalWeight += weight; // Sum
            if (sameWeight && weight != firstWeight) {
                sameWeight = false;
            }
        }
        if (totalWeight > 0 && !sameWeight) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            for (int i = 0; i < length; i++) {
                offset -= getWeight(invokers.get(i), invocation);
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        return invokers.get(ThreadLocalRandom.current().nextInt(length));
    }
```

**1.2、RoundRobinLoadBalance 轮询**

存在慢的提供者累积请求的问题，比如：第二台机器很慢，但没挂，当请求调到第二台时就卡在那，久而久之，所有请求都卡在调到第二台上。

在老的版本上，dubbo会求出最大权重和最小权重，如果权重相等，那么就直接按取模的方式，每次取完后值加一；如果权重不相等，顺序根据权重分配。

在新的版本上，对这个类进行了重构。

1. 从methodWeightMap这个实例中根据ServiceKey+MethodName的方式获取里面的一个map实例，如果没有则说明第一次进到该方法，则实例化一个放入到methodWeightMap中，并把获取到的实例命名为map
2. 遍历所有的invokers
3. 拿到当前的invoker的identifyString作为key，去map里获取weightedRoundRobin实例，如果map里没有则添加一个
4. 如果weightedRoundRobin的权重和当前invoker的权重不同，说明权重变了，需要重新设置
5. 获取当前invoker所对应的weightedRoundRobin实例中的current，并加上当前invoker的权重
6. 设置weightedRoundRobin最后的更新时间
7. maxCurrent一开始是设置的0，如果当前的weightedRoundRobin的current值大于maxCurrent则进行赋值
8. 遍历完后会得到最大的权重的invoker的selectedInvoker和这个invoker所对应的weightedRoundRobin赋值给了selectedWRR，还有权重之和totalWeight
9. 然后把selectedWRR里的current属性减去totalWeight，并返回selectedInvoker

这样看显然是不够清晰的，我们来举个例子：

```java
假定有3台dubbo provider:

10.0.0.1:20884, weight=2
10.0.0.1:20886, weight=3
10.0.0.1:20888, weight=4

totalWeight=9;

那么第一次调用的时候：
10.0.0.1:20884, weight=2    selectedWRR -> current = 2
10.0.0.1:20886, weight=3    selectedWRR -> current = 3
10.0.0.1:20888, weight=4    selectedWRR -> current = 4
 
selectedInvoker-> 10.0.0.1:20888 
调用 selectedWRR.sel(totalWeight); 
10.0.0.1:20888, weight=4    selectedWRR -> current = -5
返回10.0.0.1:20888这个实例

那么第二次调用的时候：
10.0.0.1:20884, weight=2    selectedWRR -> current = 4
10.0.0.1:20886, weight=3    selectedWRR -> current = 6
10.0.0.1:20888, weight=4    selectedWRR -> current = -1

selectedInvoker-> 10.0.0.1:20886 
调用 selectedWRR.sel(totalWeight); 
10.0.0.1:20886 , weight=4   selectedWRR -> current = -3
返回10.0.0.1:20886这个实例

那么第三次调用的时候：
10.0.0.1:20884, weight=2    selectedWRR -> current = 6
10.0.0.1:20886, weight=3    selectedWRR -> current = 0
10.0.0.1:20888, weight=4    selectedWRR -> current = 3

selectedInvoker-> 10.0.0.1:20884
调用 selectedWRR.sel(totalWeight); 
10.0.0.1:20884, weight=2    selectedWRR -> current = -3
返回10.0.0.1:20884这个实例
```













