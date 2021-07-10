# ThreadLocal原理分析

## 一、概述

ThreadLocal是面试非常高频的问题，在很多框架源码中都可以看到他的身影，比如Spring,ReentrantReadWriteLock，然后在平时的工作使用的却并不多，ThreadLocal要解决并不是多线程修改共享变量保证线程安全的问题，这个是通过悲观锁（比如synchronized）或者乐观锁（比如CAS）实现的，它要解决的问题是多线程环境下修改变量，每个线程修改自己的变量副本，线程之间互相不影响的问题。本文就介绍一下ThreadLocal是如何实现线程之间隔离的。

## 二、举例

为了方便ThreadLocal的理解，这里先举一个ThreadLocal的使用小例子，通过例子来分析它的原理。

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
public class SeqCount {
　　 // 一般使用private static修饰
    private static ThreadLocal<Integer> seqCount = new ThreadLocal<Integer>(){
        // 实现initialValue()
        public Integer initialValue() {
            return 0;
        }
    };

    public int nextSeq(){
        seqCount.set(seqCount.get() + 1);

        return seqCount.get();
    }

    public static void main(String[] args){
        SeqCount seqCount = new SeqCount();

        SeqThread thread1 = new SeqThread(seqCount);
        SeqThread thread2 = new SeqThread(seqCount);
        SeqThread thread3 = new SeqThread(seqCount);
        SeqThread thread4 = new SeqThread(seqCount);

        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
    }

    private static class SeqThread extends Thread{
        private SeqCount seqCount;

        SeqThread(SeqCount seqCount){
            this.seqCount = seqCount;
        }

        public void run() {
            for(int i = 0 ; i < 3 ; i++){
                System.out.println(Thread.currentThread().getName() + " seqCount值为 :" + seqCount.nextSeq());
            }
        }
    }
}
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

运行结果

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
Thread-0 seqCount值为 :1
Thread-0 seqCount值为 :2
Thread-0 seqCount值为 :3
Thread-1 seqCount值为 :1
Thread-1 seqCount值为 :2
Thread-1 seqCount值为 :3
Thread-2 seqCount值为 :1
Thread-2 seqCount值为 :2
Thread-2 seqCount值为 :3
Thread-3 seqCount值为 :1
Thread-3 seqCount值为 :2
Thread-3 seqCount值为 :3
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

为了对比，把上面的例子修改一下，不使用ThreadLocal看一下执行结果是怎么样的。

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
public class SeqCount1 {
    private static AtomicInteger seqCount1 = new AtomicInteger(0);

    public int nextSeq(){
        return seqCount1.incrementAndGet();
    }

    public static void main(String[] args){
        SeqCount1 seqCount = new SeqCount1();

        SeqThread thread1 = new SeqThread(seqCount);
        SeqThread thread2 = new SeqThread(seqCount);
        SeqThread thread3 = new SeqThread(seqCount);
        SeqThread thread4 = new SeqThread(seqCount);

        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
    }

    private static class SeqThread extends Thread{
        private SeqCount1 seqCount;

        SeqThread(SeqCount1 seqCount){
            this.seqCount = seqCount;
        }

        public void run() {
            for(int i = 0 ; i < 3 ; i++){
                System.out.println(Thread.currentThread().getName() + " seqCount值为 :" + seqCount.nextSeq());
            }
        }
    }
}
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

执行结果为：

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
Thread-0 seqCount值为 :1
Thread-0 seqCount值为 :2
Thread-0 seqCount值为 :3
Thread-1 seqCount值为 :4
Thread-1 seqCount值为 :5
Thread-1 seqCount值为 :6
Thread-2 seqCount值为 :7
Thread-2 seqCount值为 :9
Thread-2 seqCount值为 :10
Thread-3 seqCount值为 :8
Thread-3 seqCount值为 :11
Thread-3 seqCount值为 :12
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

通过上面两个例子大家可以清楚的看到，不使用ThreadLocal就变成了一个线程同步的问题，而使用了ThreadLocal之后线程之间就没有协作的问题，而是每个线程修改自己的变量副本，变量变成了线程内部私有的变量。

## 三、ThreadLocalMap

在上面的例子中，大家会发现使用ThreadLocal的get()、set()方法，而这些方法最后要操作就是ThreadLocalMap,所以这里先介绍一下这个东东，这个map是联系ThreadLocal和Thread的桥梁，当分析完这个map，大家对Thread,ThreadLocal,ThreadLocalMap之间的关系就会变得非常清晰。

#### ThreadLocalMap属性分析

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
//ThreadLocalMap是通过Entry实现的key-value存储
static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }
//ThreadLocalMap初始容量
private static final int INITIAL_CAPACITY = 16;
//保存Entry的数组
private Entry[] table;
//ThreadLocalMap中元素个数
private int size = 0;
//ThreadLocalMap的负载因子
private int threshold;
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

针对上面的属性，做下面几点解读：

1. 看过HashMap源码的应该有印象，HashMap实现了Map接口，而且在Map接口中也有一个Entry接口，HashMap是通过Node来保存key-value的，Node实现了Entry接口。ThreadLocalMap却完全不同，它既没有实现Map接口，在Entry中也没有类似next的指针指向下一个节点，说明ThreadLocalMap中没有使用链表，就直接存储在数组上，除此之外，ThreadLocalMap是ThreadLocal的内部类，没有使用public修饰，默认是只有当前包下面的类才可以使用，也就是说这个Map我们自己写的代码中是不能直接创建的。
2. Entry中的key就是ThreadLocal，而且这个ThreadLocal还被WeakReference包装了一下，也就是说ThreadLocal在这里是弱引用，如果ThreadLocal为null，可以直接被gc垃圾回收，关于弱引用，后面会举一个简单的例子，大家看一下即可。具体可以参考：[用弱引用堵住内存泄漏](https://www.ibm.com/developerworks/cn/java/j-jtp11225/)
3. 下面几个属性和HashMap中类似，这里有意思的一点是HashMap的负载因子是0.75，而ThreadLocalMap的负载因子是2/3。

##### **弱引用使用举例**

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
public class FinalizeTest {
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        System.out.println("finalize methode executed");
    }
    public static void main(String[] args) {
        FinalizeTest finalizeTest = new FinalizeTest();
        WeakReference<FinalizeTest> weak = new WeakReference(finalizeTest);

        Map<WeakReference<FinalizeTest>,Integer> map = new HashMap<>();
        map.put(weak,1);
        System.out.println("====第一次gc");
        System.gc();
        finalizeTest = null;
        System.out.println("====第二次gc");
        System.gc();
    }
}
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

执行结果

```
====第一次gc
====第二次gc
finalize methode executed
```

这里为了模拟ThreadLocalMap,也搞了一个Map，这个map的key也是一个使用WeakReference包装的类，事实上这个map中key的引用并没有影响gc垃圾回收，只要将对象finalizeTest设置为null，就可以正常垃圾回收，所以ThreadLocalMap中Entry节点的key的垃圾回收也是如此。ThreadLocalMap使用弱引用是为了解决内存泄漏的问题，至于什么是内存泄漏，参考：[对ThreadLocal实现原理的一点思考](https://www.jianshu.com/p/ee8c9dccc953)。

#### ThreadLocalMap构造方法分析

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
      ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
　　　　　　　//初始化数组，容量大小为16
            table = new Entry[INITIAL_CAPACITY];
　　　　　　　//通过key的hash值和15做与运算得到桶的位置
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
　　　　　　　//将key-value封装到Entry中插入数组
            table[i] = new Entry(firstKey, firstValue);
            size = 1;
　　　　　　　//设置阈值，达到这个阈值就扩容，阈值为16 * (2/3)，当然这里要取整
            setThreshold(INITIAL_CAPACITY);
        }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

构造方法很简单，就不过多介绍了。

#### ThreadLocalMap常用方法分析

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
//由于ThreadLocalMap不像HashMap，发生Hash冲突时使用链表解决，ThreadLocalMap的做法就是发生hash冲突
//会找当前位置的下一个桶
private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }
//当前位置的上一个位置
private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }
private Entry getEntry(ThreadLocal<?> key) {
　　　　　　　//确定桶的位置
            int i = key.threadLocalHashCode & (table.length - 1);
            Entry e = table[i];
　　　　　　　//如果找的位置entry不为null，并且entry正好是要找的key，就返回
            if (e != null && e.get() == key)
                return e;
            else
　　　　　　　　　　//这一步其实就是发生了hash冲突，本来应该是这个key占用的位置，却被别的key给占用了
　　　　　　　　　　//所以这里就要去数组挨个找了
                return getEntryAfterMiss(key, i, e);
        }

//通过key的hash定位到桶中entry，entry中的key和自己的key不相同，就会调用这个方法
//参数中的i就是key通过hash定位到在桶中的位置
private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                if (k == key)
                    return e;
                if (k == null)                        
                    //在这个方法中会把key对应的value给置为null，同时将entry移除
                    expungeStaleEntry(i);
                else
　　　　　　　　　　　　//如果当前桶中的entry不符合，就找后一个节点

                    i = nextIndex(i, len);
                e = tab[i];
            }
            return null;
        }
//向map中插入元素
private void set(ThreadLocal<?> key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len-1);
　　　　      //这里使用了一个for循环，寻找定位到的桶，如果定位到的桶中有元素
            //就寻找该桶之后没有存放元素的桶用来存放当前的key
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                ThreadLocal<?> k = e.get();
　　　　　　　　　　//如果key重复，用新的value覆盖旧的value
                if (k == key) {
                    e.value = value;
                    return;
                }

                if (k == null) {　　　　　　　　　　　　　　
                    //在这个方法中会检测key是否为null，如果为null就把value也置为null                       
                    //同时移除Entry节点
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            tab[i] = new Entry(key, value);
            int sz = ++size;
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
            //扩容成原来的2倍
            int newLen = oldLen * 2;
            Entry[] newTab = new Entry[newLen];
            int count = 0;
　　　　　　　//将旧数组中的元素赋值到新数组中
            for (int j = 0; j < oldLen; ++j) {
                Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocal<?> k = e.get();
                    if (k == null) {
 　　　　　　　　          //上面介绍key通过弱引用包装，可以正常GC，但是value没有使用弱引用
                        //所以在key被垃圾回收之后，value并不会被回收，所以这里手动设置为null
                        //为了帮助垃圾回收
                        e.value = null; // Help the GC
                    } else {
                        //重新定位元素在新数组中的位置
                        int h = k.threadLocalHashCode & (newLen - 1);
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            setThreshold(newLen);
            size = count;
            table = newTab;
        }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

 

上面都有注释，这里提几点需要注意的地方

- 由于ThreadLocalMap没有使用散列表的结构，所以发生hash冲突的时候是寻找下一个桶
- 把key使用弱引用，可以使得gc正常回收，但是value并不是弱引用，所以在扩容的时候，把value置为null，方便value垃圾回收，在平时写代码的时候，如果某个ThreadLocal不在使用了，最好直接调用ThreadLoalMap的remove方法把当前的key,value都移除，防止内存泄漏
- 在getEntry方法和set方法中当key为null，就把value也置为null，同时把Entry也移除了。
- 里面有些方法没有详细注释，因为并不是重要方法，所以就没有仔细看

## 四、Thread、ThreadLocal、ThreadLocalMap三者之间的的关系

在我的另一篇分析Thread的文章有提到在[Thread源码](https://www.cnblogs.com/gunduzi/p/13627469.html)中有这么一个字段，如下：

```
ThreadLocal.ThreadLocalMap threadLocals = null;
```

这个字段就是保存TreadLocalMap的，也就是说每个线程都有一个ThreadLocalMap,ThreadLocalMap中保存这个ThreadLocal和ThreadLocal封装的成员变量的值，同一个父线程的子线程的ThreadLocalMap中保存的ThreadLocal都是一样的，只是value不同，具体三者之间的关系可以用下图表示。

![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200908100450291-583670655.png)

## 五、 ThreadLocal常用方法分析

#### get方法

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
public T get() {        //获取当前线程引用
        Thread t = Thread.currentThread();        //获取当前线程的ThreadLocalMap,就是上面介绍的Thread类中的threadLocals字段
        ThreadLocalMap map = getMap(t);
        if (map != null) {            //拿到ThreadLocalMap之后，根据key获取Entry
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }　　　　//如果是首次插入，map没有创建，创建ThreadLocalMap
        return setInitialValue();
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

进入#getMap()方法

```
  ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }
```

进入#setInitialValue()方法

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
private T setInitialValue() {
　　　　　//这个方法在最开始举例的时候重写了
        T value = initialValue();
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
           //创建map
            createMap(t, value);
        return value;
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

进入#initialValue()方法

```
    protected T initialValue() {
        return null;
    }
```

这个返回的泛型T就是ThreadLocal要包装的成员变量

进入#createMap()方法

```
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }
```

直接调用上面分析的ThreadLocalMap的构造方法创建，并且给Thread中threadLocals赋值，从这里开始Thread就和ThreadLocal还有ThreadLocalMap联系起来了。

#### set()方法

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
public void set(T value) {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

这个方法很简单，就不分析了。

#### remove方法

```
     public void remove() {
         ThreadLocalMap m = getMap(Thread.currentThread());
         if (m != null)
             m.remove(this);
     }
```

这个也很简单。

## 六、常见应用场景

由于工作中基本没有使用过，所以在网上看到几个常见的使用场景，如下：

1. 把session保存到ThreadLocal中，但是现在session一般保存在redis中，用于分布式共享，使用ThreadLocal只能在一个节点的线程中共享，无法做到分布式共享，所以这个场景目前来看并不合适。
2. 由于SimpleDateFormat在格式化时间的时候，线程不安全，所以在高并发的时候格式化出来的日期可能是错误的，可以使用ThreadLocal封装SimpleDateFormat，避免每次重新创建这个对象，这个确实是一个使用场景，但是现在是java8的天下，完全可以不用这个格式化类，java8可以通过LocalDateTime获取日期时间，通过DateTimeFormatter进行格式化，这个是一个线程安全的类

　　　　　　　　![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200908111324015-268219227.png)

没有找到具体日常开发中使用ThreadLocal的场景，所以找到了源码中使用ThreadLocal的例子，就是ReentrantReadWriteLock，是一个读写锁，大家有兴趣可以看一下我的另一篇文章：[ReentrantReadWriteLock原理分析](https://www.cnblogs.com/gunduzi/p/13635002.html)



## 七、参考

[ThreadLocal原理分析](https://www.cnblogs.com/gunduzi/p/13630733.html)



## END

