# volatile介绍

## 一、概述

前面两篇文章聊了下[MESI协议](https://www.cnblogs.com/gunduzi/p/13590528.html)和[java内存模型](https://www.cnblogs.com/gunduzi/p/13594554.html)，但是都是介绍原理，没有介绍在语言级别到底是如何保证的线程安全，从本文开始就开始介绍java语言中常用的关键字和锁。本文先介绍一下volatile，这个关键字面试的时候很喜欢考察，其实面试并不是为了考察volatile的用法，而是为了考察java内存模型。

## 二、volatile作用

volatile使用方法是用来修饰公共变量，保证变量在多线程环境中保证可见性和有序性，在有些特别的条件下可以保证原子性，比如大家都知道基本数据类型除了long/double等特殊的数据类型外，赋值操作都是原子操作，但是如果long/double类型的变量前面加上volatile修饰，赋值操作也是原子的。可能有些朋友说volatile不是不能保证原子性吗，但是其实在特定的条件下是可以保证原子性的，实现方式就是通过内存屏障。下面就介绍一下可见性、有序性的实现原理。

## 三、可见性和有序性实现原理

```
public volatile boolean flag = false;
   //线程1执行
   public void setFlag(){
       //release屏障
       flag = true;
       //store屏障
   }
   //线程2执行
   public void run(){
       //load屏障
       while (flag){
       //acquire屏障
 
       }
   }
```

看到上面的各种屏障是不是有点蒙，这里说明一下。之前介绍过如下四种屏障

- LoadLoad
- LoadStore
- StoreStore
- StoreLoad

其实release就相当于StoreStore和LoadStore。acquire就相当于LoadLoad和LoadStore。至于上面几个屏障的意思，就不过多介绍，上面文章中有介绍。acquire屏障和release屏障就是实现volatile有序性的，防止指令重排。

下面介绍一下store屏障，load屏障。这两个屏障具体在硬件中的指令是什么我也不清楚，明白作用就可以了。

- load屏障：每次读取变量之前都执行refresh操作，重新从主内存读取最新的结果
- store屏障：每次更新完变量都要执行flush操作，将变量的值写回主内存，当然会通知别的CPU让他们的缓存失效

这两个屏障的作用就是实现volatile可见性。

补充：

下面这段话摘自《深入理解Java虚拟机》：

　　“观察加入volatile关键字和没有加入volatile关键字时所生成的汇编代码发现，加入volatile关键字时，会多出一个lock前缀指令”

　　lock前缀指令实际上相当于一个内存屏障，内存屏障会提供3个功能：

1. 它确保指令重排序时不会把其后面的指令排到内存屏障之前的位置，也不会把前面的指令排到内存屏障的后面；即在执行到内存屏障这句指令时，在它前面的操作已经全部完成；
2. 它会强制将对缓存的修改操作立即写入主存；
3. 如果是写操作，它会导致其他CPU中对应的缓存行无效。

## 四、volatile常见应用场景

volatile的应用要遵循下面两条原则

- 对变量的写操作不依赖于当前值。
- 该变量没有包含在具有其他变量的不变式中。

第一条很好理解，就是类似于i++这种，i即便被volatile修饰了，也没有用，无法保证原子性。

第二条是什么意思呢？没有包含在不变式中，如下代码

```
volatile int a = 0;

public void set(int value){
    if(value > a){
    
    }    
}
```

value > a就是一个不变式，在这种场景下使用volatile也是不合适的,只要无法保证被volatile修饰的关键字原子性的操作都是不合适的。

#### 应用场景一：状态标志

```
volatile` `boolean` `shutdownRequested;
 
...
 
public` `void` `shutdown() { shutdownRequested = ``true``; }
 
public` `void` `doWork() {
  ``while` `(!shutdownRequested) {
    ``// do stuff
  ``}
}
```

假设doWork()是一个线程在执行，而另一个线程会执行shutdown()，这就相当于两个线程之间实现了交互，用第二个线程可以控制第一个线程的执行。

#### 应用场景二：单例模式

```
public` `class` `Singleton {
  ``private` `Singleton() { }
  ``private` `volatile` `static` `Singleton instance;
  ``public` `Singleton getInstance(){
    ``if``(instance==``null``){
      ``synchronized` `(Singleton.``class``){
        ``if``(instance==``null``){
          ``instance = ``new` `Singleton();
        ``}
      ``}
    ``}
    ``return` `instance;
  ``}
}
```

## 五、总结

本文介绍了volatile实现有序性、可见性的原理，并且介绍了对于有些特殊的场景，volatile可以保证原子性，之后介绍了volatile一些常用的场景，上面屏障的部分说的有些混乱，没有仔细研究书籍，主要是参考网上的博客，说的很混乱，最近比较忙，等有时间好好翻翻书看看内存屏障这块。

## 六、参考

参考：[volatile介绍](https://www.cnblogs.com/gunduzi/p/13596273.html)





## END