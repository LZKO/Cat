# 零、ConcurrentHashMap源码导读

[TOC]

## 一、HashCode方法

```java
 /**
     * Spreads (XORs) higher bits of hash to lower and also forces top
     * bit to 0. Because the table uses power-of-two masking, sets of
     * hashes that vary only in bits above the current mask will
     * always collide. (Among known examples are sets of Float keys
     * holding consecutive whole numbers in small tables.)  So we
     * apply a transform that spreads the impact of higher bits
     * downward. There is a tradeoff between speed, utility, and
     * quality of bit-spreading. Because many common sets of hashes
     * are already reasonably distributed (so don't benefit from
     * spreading), and because we use trees to handle large sets of
     * collisions in bins, we just XOR some shifted bits in the
     * cheapest possible way to reduce systematic lossage, as well as
     * to incorporate impact of the highest bits that would otherwise
     * never be used in index calculations because of table bounds.
     */
    static final int spread(int h) {
        return (h ^ (h >>> 16)) & HASH_BITS;
    }
```

跟HashMap的hash算法类似，只是把位数控制在int最大整数之内。



## 二、put方法

```java
/**
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     *
     * <p>The value can be retrieved by calling the {@code get} method
     * with a key that is equal to the original key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}
     * @throws NullPointerException if the specified key or value is null
     */
    public V put(K key, V value) {
        return putVal(key, value, false);
    }
 
    /** Implementation for put and putIfAbsent */
    final V putVal(K key, V value, boolean onlyIfAbsent) {
 
        //1.校验参数是否合法
        if (key == null || value == null) throw new NullPointerException();
        int hash = spread(key.hashCode());
        int binCount = 0;
 
        //2.遍历Node
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            
            //2.1.Node为空，初始化Node
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();
 
            //2.2.CAS对指定位置的节点进行原子操作
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
                if (casTabAt(tab, i, null,
                             new Node<K,V>(hash, key, value, null)))
                    break;                   // no lock when adding to empty bin
            }
 
            //2.3.如果Node的hash值等于-1,map进行扩容
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
 
            //2.4.如果Node有值，锁定该Node。
            //如果key的hash值大于0，key的hash值和key值都相等，则替换，否则new一个新的后继Node节点存放数据。
            //如果key的hash小于0，则考虑节点是否为TreeBin实例，替换节点还是额外添加节点。
 
            else {
                V oldVal = null;
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K,V> e = f;; ++binCount) {
                                K ek;
                                if (e.hash == hash &&
                                    ((ek = e.key) == key ||
                                     (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    if (!onlyIfAbsent)
                                        e.val = value;
                                    break;
                                }
                                Node<K,V> pred = e;
                                if ((e = e.next) == null) {
                                    pred.next = new Node<K,V>(hash, key,
                                                              value, null);
                                    break;
                                }
                            }
                        }
                        else if (f instanceof TreeBin) {
                            Node<K,V> p;
                            binCount = 2;
                            if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                                           value)) != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = value;
                            }
                        }
                    }
                }
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
 
        //3.计算map的size
        addCount(1L, binCount);
        return null;
    }
```



## 三、CAS操作

compare and swap（比较并交换），在unsafe类里面有三个实现：compareAndSwapObject 、compareAndSwapInt、compareAndSwapLong。以compareAndSwapObject为例：如果内存值obj=期望值expect，则更新offset处的内存值为update。

```java
/***
   * Compares the value of the object field at the specified offset
   * in the supplied object with the given expected value, and updates
   * it if they match.  The operation of this method should be atomic,
   * thus providing an uninterruptible way of updating an object field.
   *
   * @param obj the object containing the field to modify.
   * @param offset the offset of the object field within <code>obj</code>.
   * @param expect the expected value of the field.
   * @param update the new value of the field if it equals <code>expect</code>.
   * @return true if the field was changed.
   */
  public native boolean compareAndSwapObject(Object obj, long offset,
                                             Object expect, Object update);
```

其中用到Unsafe提供了三个原子操作，如下：

```java
@SuppressWarnings("unchecked")
    static final <K,V> Node<K,V> tabAt(Node<K,V>[] tab, int i) {
 
        //获取obj对象中offset偏移地址对应的object型field的值,支持volatile load语义
        return (Node<K,V>)U.getObjectVolatile(tab, ((long)i << ASHIFT) + ABASE);
    }
 
    static final <K,V> boolean casTabAt(Node<K,V>[] tab, int i,
                                        Node<K,V> c, Node<K,V> v) {
 
        //在obj的offset位置比较object field和期望的值，如果相同则更新。
        return U.compareAndSwapObject(tab, ((long)i << ASHIFT) + ABASE, c, v);
    }
 
    static final <K,V> void setTabAt(Node<K,V>[] tab, int i, Node<K,V> v) {
 
        //设置obj对象中offset偏移地址对应的object型field的值为指定值。
        U.putObjectVolatile(tab, ((long)i << ASHIFT) + ABASE, v);
    }
```

初始化表如下：

```
/**
     * Table initialization and resizing control.  When negative, the
     * table is being initialized or resized: -1 for initialization,
     * else -(1 + the number of active resizing threads).  Otherwise,
     * when table is null, holds the initial table size to use upon
     * creation, or 0 for default. After initialization, holds the
     * next element count value upon which to resize the table.
     */
   
    private transient volatile int sizeCtl;
 
 
    /**
     * Initializes table, using the size recorded in sizeCtl.
     */
    private final Node<K,V>[] initTable() {
        Node<K,V>[] tab; int sc;
        while ((tab = table) == null || tab.length == 0) {
 
            //如果当前Node正在进行初始化或者resize(),则线程从运行状态变成可执行状态，cpu会从可运行状态的线程中选择
            if ((sc = sizeCtl) < 0)
                Thread.yield(); // lost initialization race; just spin
 
            //如果当前Node没有初始化或者resize()操作，那么创建新Node节点，并给sizeCtl重新赋值
            else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                try {
                    if ((tab = table) == null || tab.length == 0) {
                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                        @SuppressWarnings("unchecked")
                        Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                        table = tab = nt;
                        sc = n - (n >>> 2);
                    }
                } finally {
                    sizeCtl = sc;
                }
                break;
            }
        }
        return tab;
    }
```

## 四、size()方法

HashMap的size()是在put的时候对size进行++size的操作，每增加一个元素size大小自增1，调用size()的时候，直接赋值即可获得。

ConcurrentHashMap的计算map中映射的个数，可以由mappingCount完全替代，因为ConcurrentHashMap可能包含的映射数多于返回值为为int的映射数。该返回的值是估计值，实际数量可能会因为并发插入或删除而有所不同。

ConcurrentHashMap的size方法是一个估计值，并不是准确值。

```java
public int size() {
        long n = sumCount();
        return ((n < 0L) ? 0 :
                (n > (long)Integer.MAX_VALUE) ? Integer.MAX_VALUE :
                (int)n);
  }
 
  final long sumCount() {
        CounterCell[] as = counterCells; CounterCell a;
        long sum = baseCount;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    sum += a.value;
            }
        }
        return sum;
  }
 
 /**
     * Returns the number of mappings. This method should be used
     * instead of {@link #size} because a ConcurrentHashMap may
     * contain more mappings than can be represented as an int. The
     * value returned is an estimate; the actual count may differ if
     * there are concurrent insertions or removals.
     *
     * @return the number of mappings
     * @since 1.8
     */
    public long mappingCount() {
        long n = sumCount();
        return (n < 0L) ? 0L : n; // ignore transient negative values
    }
```



## 五、参考

[源码分析——ConcurrentHashMap的spread,put,size方法原理分析](https://blog.csdn.net/wangmei4968/article/details/90287414)



# 另一种解读

# 一、综述

## 1、概述

ConcurrentHashMap，一个线程安全的高性能集合，存储结构和HashMap一样，都是采用数组进行分桶，之后再每个桶中挂一个链表，当链表长度大于8的时候转为红黑树，其实现线程安全的基本原理是采用CAS + synchronized组合，当数组的桶中没有元素时采用CAS插入，相反，则采用synchronized加锁插入，除此之外在扩容和记录size方面也做了很多的优化，扩容允许多个线程共同协助扩容，而记录size的方式则采用类似LongAddr的方式，提高并发性，本片文章是介绍ConcurrentHashMap的第一篇，主要介绍下其结构，put()、get()方法，后面几篇文章会介绍其他方法。

## 2、ConcurrentHashMap存储结构

![](C:\lyy\project_workspace\Cat\src\main\resources\pic\ConcurrentHashMap-1.png)

从上图可以清晰的看到其存储结构是采用数组 + 链表 + 红黑树的结构，下面就介绍一下每一种存储结构在代码中的表现形式。

**数组**

```
    transient volatile Node<K,V>[] table;
    private transient volatile Node<K,V>[] nextTable;
```

可以看到数组中存的是Node，Node就是构成链表的节点。第二个nextTable是扩容之后的数组，在扩容的时候会使用。

**链表**

```
static class Node<K,V> implements Map.Entry<K,V> {
        final int hash;
        final K key;
        volatile V val;
        volatile Node<K,V> next;

        Node(int hash, K key, V val, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.val = val;
            this.next = next;
        }
//省略部分代码
}
```

一个典型的单链表存储结构，里面保存着key,val，以及这个key对应的hash值，next表示指向下一个Node。

**红黑树**

```
static final class TreeNode<K,V> extends Node<K,V> {
        TreeNode<K,V> parent;  // red-black tree links
        TreeNode<K,V> left;
        TreeNode<K,V> right;
        TreeNode<K,V> prev;    // needed to unlink next upon deletion
        boolean red;

        TreeNode(int hash, K key, V val, Node<K,V> next,
                 TreeNode<K,V> parent) {
            super(hash, key, val, next);
            this.parent = parent;
        }
//省略部分代码
}
```

TreeNode是构成红黑树的节点，其继承了Node节点，用于保存key,val,hash等值。但是在数组中并不直接保存TreeNode，一开始在没看源码之前，我以为数组中保存的是红黑树的根节点，其实不是，是下面这个东东。

```
static final class TreeBin<K,V> extends Node<K,V> {
        TreeNode<K,V> root;
        volatile TreeNode<K,V> first;
        volatile Thread waiter;
        volatile int lockState;
        // values for lockState
        static final int WRITER = 1; // set while holding write lock
        static final int WAITER = 2; // set when waiting for write lock
        static final int READER = 4; // increment value for setting read lock
//省略部分代码
)
```

这个类封装了TreeNode,而且提供了链表转红黑树，以及红黑树的增删改查方法。

**其他节点**

```
 static final class ForwardingNode<K,V> extends Node<K,V> {
        final Node<K,V>[] nextTable;
        ForwardingNode(Node<K,V>[] tab) {
            super(MOVED, null, null, null);
            this.nextTable = tab;
        }
//省略部分代码
}
```

这个节点正常情况下在ConcurrentHashMap中是不存在的，只有当扩容的时候才会存在，该节点中有一个nextTable字段，用于指向扩容之后的数组，其使用方法是这样的，扩容的时候需要把旧数组的数据拷贝到新数组，当某个桶中的数据被拷贝完成之后，就把旧数组的该桶标记为ForwardingNode，当别的线程访问到这个桶，发现被标记为ForwardingNode就知道该桶已经被copy到了新数组，之后就可以根据这个做相应的处理。

## 3、ConcurrentHashMap关键属性分析

这些属性先有个印象，都会在之后的源码中使用，不用现在就搞明白。

```
 //最大容量
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    //默认初始化容量
    private static final int DEFAULT_CAPACITY = 16;
    //负载因子
    private static final float LOAD_FACTOR = 0.75f;
    //链表转为红黑树的临界值
    static final int TREEIFY_THRESHOLD = 8;
    //红黑树转为链表的临界值
    static final int UNTREEIFY_THRESHOLD = 6;
    //当容量大于64时，链表才会转为红黑树，否则，即便链表长度大于8，也不会转，而是会扩容
    static final int MIN_TREEIFY_CAPACITY = 64;
    //以上的几个属性和HashMap一模一样


    //扩容相关，每个线程负责最小桶个数
    private static final int MIN_TRANSFER_STRIDE = 16;
    //扩容相关，为了计算sizeCtl
    private static int RESIZE_STAMP_BITS = 16;
    //最大辅助扩容线程数量
    private static final int MAX_RESIZERS = (1 << (32 - RESIZE_STAMP_BITS)) - 1;
    //扩容相关，为了计算sizeCtl
    private static final int RESIZE_STAMP_SHIFT = 32 - RESIZE_STAMP_BITS;
    //下面几个是状态值
    //MOVED表示正在扩容
    static final int MOVED     = -1; // hash for forwarding nodes
    //-2表示红黑树标识
    static final int TREEBIN   = -2; // hash for roots of trees
    static final int RESERVED  = -3; // hash for transient reservations
    //计算Hash值使用
    static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash
    //可用CPU核数
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    //用于记录容器中插入的元素数量
    private transient volatile long baseCount;
    //这个sizeCtl非常重要，基本上在代码中到处可以看到它的身影，后面会单独分析一下
    private transient volatile int sizeCtl;
    //扩容相关
    private transient volatile int transferIndex;
    //计算容器size相关
    private transient volatile int cellsBusy;
    //计算容器size相关，在介绍相关代码的时候详细介绍
    private transient volatile CounterCell[] counterCells;
```

上面的最开始的几个属性应该很好理解，后面的几个属性可能不知道有什么用，没关系，等到介绍相关代码的时候都会介绍的，这里着重介绍下sizeCtl,这个字段控制着扩容和table初始化，在不同的地方有不同的用处，下面列举一下其每个标识的意思：

- 负数代表正在进行初始化或扩容操作
- -1代表正在初始化
- -N 表示，这个高16位表示当前扩容的标志，每次扩容都会生成一个不一样的标志，低16位表示参与扩容的线程数量
- 正数或0，0代表hash表还没有被初始化，正数表示达到这个值需要扩容，其实就等于(容量 * 负载因子)

## 4、CAS操作

上面介绍了ConcurrentHashMap是通过CAS + synchronized保证线程安全的，那CAS操作有哪些，如下：

```
//获取数组中对应索引的值    static final <K,V> Node<K,V> tabAt(Node<K,V>[] tab, int i) {
        return (Node<K,V>)U.getObjectVolatile(tab, ((long)i << ASHIFT) + ABASE);
    }
　　　//修改数组对应索引的值，这个是真正的CAS操作
    static final <K,V> boolean casTabAt(Node<K,V>[] tab, int i,
                                        Node<K,V> c, Node<K,V> v) {
        return U.compareAndSwapObject(tab, ((long)i << ASHIFT) + ABASE, c, v);
    }
    //设置数组对应索引的值
    static final <K,V> void setTabAt(Node<K,V>[] tab, int i, Node<K,V> v) {
        U.putObjectVolatile(tab, ((long)i << ASHIFT) + ABASE, v);
    }
```

上面三个方法，我看很多文章把这三个方法都归类为CAS操作，其实第一个和第三个我觉得并不是，比如第一个方法，只是强制从主内存获取数据，第三个方法是修改完数据之后强制刷新到主内存，同时通知其他线程失效，只是为了保证可见性，而且这两个要求被修改的对象一定要被volatile修饰，这也是上面在介绍table的时候被volatile修饰的原因。

## 5、put()方法

put方法实际调用的是putVal()方法，下面分析下putVal方法。

```
final V putVal(K key, V value, boolean onlyIfAbsent) {
        if (key == null || value == null) throw new NullPointerException();
        //这个计算hash值的方法和hashMap不同
        int hash = spread(key.hashCode());
        //记录链表节点个数
        int binCount = 0;
        //这个死循环的作用是为了保证CAS一定可以成功，否则就一直重试
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            //如果table还没有初始化，初始化
            if (tab == null || (n = tab.length) == 0)
                //初始化数组，后面会分析，说明1
                tab = initTable();
            //如果通过hash值定位到桶的位置为null，直接通过CAS插入，上面死循环就是为了这里
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
                if (casTabAt(tab, i, null,
                             new Node<K,V>(hash, key, value, null)))
                    break;                   // no lock when adding to empty bin
            }
            //如果发现节点的Hash值为MOVED，协助扩容，至于为什么hash值会为MOVEN，后面会说明，说明2
            else if ((fh = f.hash) == MOVED)
                //协助扩容，在讲解扩容的时候再讲解
                tab = helpTransfer(tab, f);
            else {
                //到这里说明桶中有值
                V oldVal = null;
                //不管是链表还是红黑树都加锁处理，防止别的线程修改
                synchronized (f) {
                    //这里直接从主内存重新获取，双重检验，防止已经被别的线程修改了
                    if (tabAt(tab, i) == f) {
                        //fh >= 0，说明是链表，为什么fh>=0就是链表，这个就是hash值计算的神奇的地方，所有的key的hash都是大于等于0的，
                        //红黑树的hash值为-2，至于为什么为-2后面会说明，说明3
                        if (fh >= 0) {
                            //这里就开始记录链表中节点个数了，为了转为红黑树做好记录
                            binCount = 1;
                            //for循环遍历链表
                            for (Node<K,V> e = f;; ++binCount) {
                                K ek;
                                //如果key相同，就替换value
                                if (e.hash == hash &&
                                    ((ek = e.key) == key ||
                                     (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    //这个参数传的是false
                                    if (!onlyIfAbsent)
                                        e.val = value;
                                    break;
                                }
                                //遍历没有发现有相同key的，就挂在链表的末尾
                                Node<K,V> pred = e;
                                if ((e = e.next) == null) {
                                    pred.next = new Node<K,V>(hash, key,
                                                              value, null);
                                    break;
                                }
                            }
                        }
                        //如果是红黑树，这里就是上面介绍的，数组中存的不是TreeNode,而是TreeBin
                        else if (f instanceof TreeBin) {
                            Node<K,V> p;
                            binCount = 2;
                            //向红黑树插入
                            if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                                           value)) != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = value;
                            }
                        }
                    }
                }
                if (binCount != 0) {
                    //如果链表长度大于等于8，转为红黑树,至于怎么转在介绍红黑树部分的时候再详细说
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        //计算size++,不过是线程安全的方式，这里这篇文章先不介绍，之后会专门介绍
        addCount(1L, binCount);
        return null;
    }
```

整个过程梳理如下：

1. 数组没有初始化就先初始化数组
2. 计算当前插入的key的hash值
3. 根据第二步的hash值定位到桶的位置，如果为null，直接CAS自旋插入
4. 如果是链表就遍历链表，有相同的key就替换，没有就插入到链表尾部
5. 如果是红黑树直接插入
6. 判断链表长度是否超过8，超过就转为红黑树
7. ConcurrentHashMap元素个数加1

上面代码中标红的地方说明：

**说明一：**initTable()

```
private final Node<K,V>[] initTable() {
        Node<K,V>[] tab; int sc;
        while ((tab = table) == null || tab.length == 0) {
            //如果这个值小于零，说明有别的线程在初始化
            if ((sc = sizeCtl) < 0)
                //让出CPU时间，注意这时线程依然是RUNNABLE状态
                //这里使用yield没有风险，因为即便这个线程又竞争到CPU，再次循环到这里它还会让出CPU的
                Thread.yield(); // lost initialization race; just spin
            //初始状态SIZECTL为0，通过CAS修改为-1
            else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                try {
                    if ((tab = table) == null || tab.length == 0) {
                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                        @SuppressWarnings("unchecked")
                        //初始化
                        Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                        table = tab = nt;
                        //扩容点，比如n = 16,最后计算出来的sc = 12
                        sc = n - (n >>> 2);
                    }
                } finally {
                    sizeCtl = sc;
                }
                break;
            }
        }
        return tab;
    }
```

**说明二：**扩容状态为什么hash为MOVED

```
//构造方法，里面使用super，也就是他的父类Node的构造方法   
 ForwardingNode(Node<K,V>[] tab) {
            super(MOVED, null, null, null);
            this.nextTable = tab;
        }
```

上面介绍ForwardingNode的时候说过，这个是扩容的时候，如果这个桶处理过了就设置为该节点，这个类的构造方法可以看出，它会把hash值设置为MOVED状态。

**说明三：**红黑树TreeBin的hash值为什么为-2

```
TreeBin(TreeNode<K,V> b) {
            super(TREEBIN, null, null, null);
            this.first = b;
            TreeNode<K,V> r = null;
            for (TreeNode<K,V> x = b, next; x != null; x = next) {
                next = (TreeNode<K,V>)x.next;
                x.left = x.right = null;
                if (r == null) {
                    x.parent = null;
                    x.red = false;
                    r = x;
                }
//省略部分代码
}
```

这个是TreeBin的构造方法，这个super同样是Node的构造方法，hash值为TREEBIN = -2

## 6、get()方法

```
 public V get(Object key) {
        Node<K,V>[] tab; Node<K,V> e, p; int n, eh; K ek;        //计算key的hash值
        int h = spread(key.hashCode());        //数组不为空，获取对应桶的值
        if ((tab = table) != null && (n = tab.length) > 0 &&
            (e = tabAt(tab, (n - 1) & h)) != null) {            //获取到，直接返回value
            if ((eh = e.hash) == h) {
                if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                    return e.val;
            }            //小于0，就是上面介绍的TREEBIN状态，是红黑树，在红黑树中查找
            else if (eh < 0)
                return (p = e.find(h, key)) != null ? p.val : null;            //链表的处理方法，一个一个遍历
            while ((e = e.next) != null) {
                if (e.hash == h &&
                    ((ek = e.key) == key || (ek != null && key.equals(ek))))
                    return e.val;
            }
        }
        return null;
    }
```

get方法很简单，就是去各个数据结构中找，不过红黑树的遍历还是要好好看看的，这里先不分析，红黑树这玩意为了实现自平衡，定义了很多的限制条件，实现起来的复杂度真是爆炸，之后文章会分析，不过代码看的我都快吐了，哈哈哈。

## 7、总结

本篇文章就先分析到这，不然就太长了，本文介绍了ConcurrentHashMap的存储结构，节点构成，以及初始化方法，put和get方法，整体来说这部分比较简单，ConcurrentHashMap复杂的部分是扩容和计数，当然我自己觉得红黑树部分是最复杂的，后面再慢慢介绍。

## 8、参考

[ConcurrentHashMap原理分析(一)-综述](https://www.cnblogs.com/gunduzi/p/13649860.html)



# 二、扩容

## 1、概述

在[上一篇文章](https://www.cnblogs.com/gunduzi/p/13649860.html)中介绍了ConcurrentHashMap的存储结构，以及put和get方法，那本篇文章就介绍一下其扩容原理。其实说到扩容，无非就是新建一个数组，然后把旧的数组中的数据拷贝到新的数组中，在HashMap的实现中，由于没有加锁，可能会同时有多个线程创建了多个数组，而且拷贝的时候也没有加锁，所以在多线程的时候非常混乱，当然HashMap本身设计就是线程不安全的，那要实现一个好的扩容，要解决以下几点问题：

- 创建新数组的时候，只能由一个线程创建
- 拷贝数据的时候，已经拷贝过的数据不能重复拷贝
- 拷贝数据的时候，一个桶只能由一个线程负责，不能多个线程一起拷贝一个桶的数据
- 多个线程如何协作，加速扩容过程

其实以上几点问题在ConcurrentHashMap都被解决了，下面就带着上面几个问题来分析扩容的源码。

## 2、扩容的触发点

既然要分析扩容，就要先分析一下在什么情况下会进行扩容。总体来说有两种情况。

### 情况一

集合中的元素个数达到负载因子规定的大小，比如数组初始化容量是16，负载因子0.75，那达到12个元素的时候就要扩容，下面就看一下触发代码。

```
private final void addCount(long x, int check) {
      //省略部分代码，省略部分是处理计数的
        if (check >= 0) {
            Node<K,V>[] tab, nt; int n, sc;
            //sizeCtl正常就是值就是扩容点的值，首次设置在initTable方法设置的
            //s >= sizeCtl说明达到了扩容点，后面两个方法是为了处理极端的情况的
            while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
                   (n = tab.length) < MAXIMUM_CAPACITY) {
                //这个方法的目的就是为每次扩容生成一个唯一的标识，在第一篇文章中介绍属性的时候
                //介绍了好几个属性，都是在这个方法中使用的，后面会分析这个方法，标记：说明1
                int rs = resizeStamp(n);
                //sc小于零说明在扩容中，设置小于0的是下面哪个else if设置的
                if (sc < 0) {
                    //这个是判断sc的高16位是不是和rs相等，一会分析rs就知道了，只要处在同一轮扩容中，这个标志就是一样的
                    //后面几个方法都是在处理一些极端情况，最后一个transferIndex <= 0这个一会需要说明下，标记：说明2
                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                        sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
                        transferIndex <= 0)
                        break;
                    //上面的条件就是判断一些极端条件，如果符合，上面就直接break了，如果不满足就通过CAS将sc加1
                    //这个其实就是把sizeCtl的低16位加1，意思是又多了一个协助扩容的线程，至于为什么要加1后面说明，标记：说明3
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                        //这里由于是协助扩容，所以传入的是nt = nextTable，因为已经创建好了
                        //这里其实就是解释了概述中提到的第一个问题，一次只能由一个线程创建表
                        transfer(tab, nt);
                }
                //如果sc >= 0,说明nextTable还没有创建，通过CAS竞争去创建
                //注意这里把sizeCtl加2，不是加1，意思可能是一方面要创建表，另一方面要扩容                //如果CAS成功，说明只能由要给线程去创建表
                else if (U.compareAndSwapInt(this, SIZECTL, sc,
                                             (rs << RESIZE_STAMP_SHIFT) + 2))
                    //这里传入null
                    transfer(tab, null);
                //计算元素个数，与此次扩容无关
                s = sumCount();
            }
        }

}
```

**说明1**

```
    static final int resizeStamp(int n) {
        return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
    }
```

- Integer.numberOfLeadingZeros表示一个数从高位算起为0的位的个数，比如当n = 1时，结果为31，当n = 2时，结果为30。
- 1 << (RESIZE_STAMP_BITS - 1)，由于RESIZE_STAMP_BITS = 16，所以这个就是把1的二进制左移15位，也就是2^16，2的16次方。
- 上面两个结果做或运算，就相当于两个数向加，因为第二数的低16位全是0。假设n = 16，最后的结果为：32795
- 由于每次传入的n不相同，所以每次结果也不同，也就是每次的标识也不同，这个值这么做的好处就是只在低16位有值，在下面计算sizeCtl的时候，只要继续左移16位，那低16位也就没有值了

**说明2**

```
sc >>> RESIZE_STAMP_SHIFT) != rs
```

这段代码是否成立，要想搞清楚这段代码是否成立，要先搞清楚sc是多少。

```
 while (s >= (long)(sc = sizeCtl)
```

从while循环看，sc = sizeCtl，那sizeCtl是多少，如下：

```
 else if (U.compareAndSwapInt(this, SIZECTL, sc,
                                             (rs << RESIZE_STAMP_SHIFT) + 2))
```

这个CAS操作，会修改sizeCtl的值，最后sizeCtl = ( (rs << 16) + 2)，可以知道最开始的那个不等式就相当于如下：

```
((rs << RESIZE_STAMP_SHIFT) + 2) >>> RESIZE_STAMP_SHIFT != rs
```

上面这个公式就很清楚了，相当于rs先有符号左移16位，之后加2，最后再无符号右移16位，由于加的2在低位，右移的时候就没了，所以最后的结果还是rs。

**说明3**

经过上面两个说明，应该可以清楚的知道sizeCtl的高16位是标志位，就是每一轮扩容生成的一个唯一的标志，低16位标识参与扩容的线程数，所以这里进行加1操作。那问题来了，为什么要记录参与扩容的线程数？这个原因一会看扩容的代码就明白了，这里先提一下，记录参与扩容的线程数的原因是每个线程执行完扩容，sizeCtl就减1，当最后发现sizeCtl = rs <<RESIZE_STAMP_SHIFT的时候，说明所有参与扩容的线程都执行完，防止最后以为扩容结束了，旧的数组都被干掉了，但是还有的线程在copy。

### 情况二

上面只是分析了情况一，还有另一种情况也会扩容，就是当容量小于64，但是链表中发生hash冲突的节点个数大于等于8，这时也会扩容。

```
private final void treeifyBin(Node<K,V>[] tab, int index) {
        Node<K,V> b; int n, sc;
        if (tab != null) {
            //容量小于64
            if ((n = tab.length) < MIN_TREEIFY_CAPACITY)
               //扩容
                tryPresize(n << 1);
//省略部分代码
}
```

这个是链表转为红黑树的方法，里面的tryPressize就是扩容，下面分析一下这个方法。

```
private final void tryPresize(int size) {
        int c = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY :            //该方法就是生成最小的大于当前或等当前数字的2的倍数
            tableSizeFor(size + (size >>> 1) + 1);
        int sc;
        while ((sc = sizeCtl) >= 0) {
            Node<K,V>[] tab = table; int n;            //这一部分就不分析，既然都hash冲突了，tab一定存在
            if (tab == null || (n = tab.length) == 0) {
                n = (sc > c) ? sc : c;
                if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                    try {
                        if (table == tab) {
                            @SuppressWarnings("unchecked")
                            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                            table = nt;
                            sc = n - (n >>> 2);
                        }
                    } finally {
                        sizeCtl = sc;
                    }
                }
            }
            else if (c <= sc || n >= MAXIMUM_CAPACITY)
                break;            //这一部分就可以去扩容的代码，可以看到和刚刚那个方法写的基本上一摸一样，就不重复了
            else if (tab == table) {
                int rs = resizeStamp(n);
                if (sc < 0) {
                    Node<K,V>[] nt;
                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                        sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
                        transferIndex <= 0)
                        break;
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                        transfer(tab, nt);
                }
                else if (U.compareAndSwapInt(this, SIZECTL, sc,
                                             (rs << RESIZE_STAMP_SHIFT) + 2))
                    transfer(tab, null);
            }
        }
    }
```

## 3、transfer()方法

下面正式进入分析扩容的方法,这部分参考：[并发编程——ConcurrentHashMap#transfer() 扩容逐行分析](https://juejin.im/post/6844903607901356046)

```
/**
 * Moves and/or copies the nodes in each bin to new table. See
 * above for explanation.
 * 
 * transferIndex 表示转移时的下标，初始为扩容前的 length。
 * 
 * 我们假设长度是 32
 */
private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
    int n = tab.length, stride;
    // 将 length / 8 然后除以 CPU核心数。如果得到的结果小于 16，那么就使用 16。
    // 这里的目的是让每个 CPU 处理的桶一样多，避免出现转移任务不均匀的现象，如果桶较少的话，默认一个 CPU（一个线程）处理 16 个桶
    if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
        stride = MIN_TRANSFER_STRIDE; // subdivide range 细分范围 stridea：TODO
    // 新的 table 尚未初始化
    if (nextTab == null) {            // initiating
        try {
            // 扩容  2 倍
            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
            // 更新
            nextTab = nt;
        } catch (Throwable ex) {      // try to cope with OOME
            // 扩容失败， sizeCtl 使用 int 最大值。
            sizeCtl = Integer.MAX_VALUE;
            return;// 结束
        }
        // 更新成员变量
        nextTable = nextTab;
        // 更新转移下标，就是 老的 tab 的 length
        transferIndex = n;
    }
    // 新 tab 的 length
    int nextn = nextTab.length;
    // 创建一个 fwd 节点，用于占位。当别的线程发现这个槽位中是 fwd 类型的节点，则跳过这个节点。
    ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);
    // 首次推进为 true，如果等于 true，说明需要再次推进一个下标（i--），反之，如果是 false，那么就不能推进下标，需要将当前的下标处理完毕才能继续推进
    boolean advance = true;
    // 完成状态，如果是 true，就结束此方法。
    boolean finishing = false; // to ensure sweep before committing nextTab
    // 死循环,i 表示下标，bound 表示当前线程可以处理的当前桶区间最小下标，死循环的作用是保证拷贝全部完成。
    for (int i = 0, bound = 0;;) {
        Node<K,V> f; int fh;
        // 如果当前线程可以向后推进；这个循环就是控制 i 递减。同时，每个线程都会进入这里取得自己需要转移的桶的区间        //这个循环只是用来控制每个线程每轮最多copy的桶的个数，如果只有一个线程在扩容，也是可以完成的，只是分成多轮
        while (advance) {
            int nextIndex, nextBound;
            // 对 i 减一，判断是否大于等于 bound （正常情况下，如果大于 bound 不成立，说明该线程上次领取的任务已经完成了。那么，需要在下面继续领取任务）
            // 如果对 i 减一大于等于 bound（还需要继续做任务），或者完成了，修改推进状态为 false，不能推进了。任务成功后修改推进状态为 true。
            // 通常，第一次进入循环，i-- 这个判断会无法通过，从而走下面的 nextIndex 赋值操作（获取最新的转移下标）。其余情况都是：如果可以推进，            //将 i 减一，然后修改成不可推进。如果 i 对应的桶处理成功了，改成可以推进。
            if (--i >= bound || finishing)
                advance = false;// 这里设置 false，是为了防止在没有成功处理一个桶的情况下却进行了推进
            // 这里的目的是：1. 当一个线程进入时，会选取最新的转移下标。2. 当一个线程处理完自己的区间时，如果还有剩余区间的没有别的线程处理。再次获取区间。
            else if ((nextIndex = transferIndex) <= 0) {
                // 如果小于等于0，说明没有区间了 ，i 改成 -1，推进状态变成 false，不再推进，表示，扩容结束了，当前线程可以退出了
                // 这个 -1 会在下面的 if 块里判断，从而进入完成状态判断
                i = -1;
                advance = false;// 这里设置 false，是为了防止在没有成功处理一个桶的情况下却进行了推进
            }// CAS 修改 transferIndex，即 length - 区间值，留下剩余的区间值供后面的线程使用
            else if (U.compareAndSwapInt
                     (this, TRANSFERINDEX, nextIndex,
                      nextBound = (nextIndex > stride ?
                                   nextIndex - stride : 0))) {
                bound = nextBound;// 这个值就是当前线程可以处理的最小当前区间最小下标
                i = nextIndex - 1; // 初次对i 赋值，这个就是当前线程可以处理的当前区间的最大下标
                advance = false; // 这里设置 false，是为了防止在没有成功处理一个桶的情况下却进行了推进，这样对导致漏掉某个桶。下面的 if (tabAt(tab, i) == f) 判断会出现这样的情况。
            }
        }// 如果 i 小于0 （不在 tab 下标内，按照上面的判断，领取最后一段区间的线程扩容结束）
        //  如果 i >= tab.length(不知道为什么这么判断)
        //  如果 i + tab.length >= nextTable.length  （不知道为什么这么判断）
        if (i < 0 || i >= n || i + n >= nextn) {
            int sc;
            if (finishing) { // 如果完成了扩容
                nextTable = null;// 删除成员变量
                table = nextTab;// 更新 table
                sizeCtl = (n << 1) - (n >>> 1); // 更新阈值
                return;// 结束方法。
            }// 如果没完成             //说明1
            if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {// 尝试将 sc -1. 表示这个线程结束帮助扩容了，将 sc 的低 16 位减一。
                if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)// 如果 sc - 2 不等于标识符左移 16 位。如果他们相等了，说明没有线程在帮助他们扩容了。也就是说，扩容结束了。
                    return;// 不相等，说明没结束，当前线程结束方法。
                finishing = advance = true;// 如果相等，扩容结束了，更新 finising 变量
                i = n; // 再次循环检查一下整张表
            }
        }
        else if ((f = tabAt(tab, i)) == null) // 获取老 tab i 下标位置的变量，如果是 null，就使用 fwd 占位。
            advance = casTabAt(tab, i, null, fwd);// 如果成功写入 fwd 占位，再次推进一个下标
        else if ((fh = f.hash) == MOVED)// 如果不是 null 且 hash 值是 MOVED。
            advance = true; // already processed // 说明别的线程已经处理过了，再次推进一个下标
        else {// 到这里，说明这个位置有实际值了，且不是占位符。对这个节点上锁。为什么上锁，防止 putVal 的时候向链表插入数据
            synchronized (f) {
                // 判断 i 下标处的桶节点是否和 f 相同
                if (tabAt(tab, i) == f) {
                    Node<K,V> ln, hn;// low, height 高位桶，低位桶
                    // 如果 f 的 hash 值大于 0 。TreeBin 的 hash 是 -2
                    if (fh >= 0) {
                        // 对老长度进行与运算（第一个操作数的的第n位于第二个操作数的第n位如果都是1，那么结果的第n为也为1，否则为0）
                        // 由于 Map 的长度都是 2 的次方（000001000 这类的数字），那么取于 length 只有 2 种结果，一种是 0，一种是1
                        //  如果是结果是0 ，Doug Lea 将其放在低位，反之放在高位，目的是将链表重新 hash，放到对应的位置上，让新的取于算法能够击中他。
                        int runBit = fh & n;
                        Node<K,V> lastRun = f; // 尾节点，且和头节点的 hash 值取于不相等
                        // 遍历这个桶                        //说明2
                        for (Node<K,V> p = f.next; p != null; p = p.next) {
                            // 取于桶中每个节点的 hash 值
                            int b = p.hash & n;
                            // 如果节点的 hash 值和首节点的 hash 值取于结果不同
                            if (b != runBit) {
                                runBit = b; // 更新 runBit，用于下面判断 lastRun 该赋值给 ln 还是 hn。
                                lastRun = p; // 这个 lastRun 保证后面的节点与自己的取于值相同，避免后面没有必要的循环
                            }
                        }
                        if (runBit == 0) {// 如果最后更新的 runBit 是 0 ，设置低位节点
                            ln = lastRun;
                            hn = null;
                        }
                        else {
                            hn = lastRun; // 如果最后更新的 runBit 是 1， 设置高位节点
                            ln = null;
                        }// 再次循环，生成两个链表，lastRun 作为停止条件，这样就是避免无谓的循环（lastRun 后面都是相同的取于结果）
                        for (Node<K,V> p = f; p != lastRun; p = p.next) {
                            int ph = p.hash; K pk = p.key; V pv = p.val;
                            // 如果与运算结果是 0，那么就还在低位
                            if ((ph & n) == 0) // 如果是0 ，那么创建低位节点
                                ln = new Node<K,V>(ph, pk, pv, ln);
                            else // 1 则创建高位
                                hn = new Node<K,V>(ph, pk, pv, hn);
                        }
                        // 其实这里类似 hashMap 
                        // 设置低位链表放在新链表的 i
                        setTabAt(nextTab, i, ln);
                        // 设置高位链表，在原有长度上加 n
                        setTabAt(nextTab, i + n, hn);
                        // 将旧的链表设置成占位符
                        setTabAt(tab, i, fwd);
                        // 继续向后推进
                        advance = true;
                    }// 如果是红黑树
                    else if (f instanceof TreeBin) {
                        TreeBin<K,V> t = (TreeBin<K,V>)f;
                        TreeNode<K,V> lo = null, loTail = null;
                        TreeNode<K,V> hi = null, hiTail = null;
                        int lc = 0, hc = 0;
                        // 遍历
                        for (Node<K,V> e = t.first; e != null; e = e.next) {
                            int h = e.hash;
                            TreeNode<K,V> p = new TreeNode<K,V>
                                (h, e.key, e.val, null, null);
                            // 和链表相同的判断，与运算 == 0 的放在低位
                            if ((h & n) == 0) {
                                if ((p.prev = loTail) == null)
                                    lo = p;
                                else
                                    loTail.next = p;
                                loTail = p;
                                ++lc;
                            } // 不是 0 的放在高位
                            else {
                                if ((p.prev = hiTail) == null)
                                    hi = p;
                                else
                                    hiTail.next = p;
                                hiTail = p;
                                ++hc;
                            }
                        }
                        // 如果树的节点数小于等于 6，那么转成链表，反之，创建一个新的树
                        ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) :
                            (hc != 0) ? new TreeBin<K,V>(lo) : t;
                        hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
                            (lc != 0) ? new TreeBin<K,V>(hi) : t;
                        // 低位树
                        setTabAt(nextTab, i, ln);
                        // 高位数
                        setTabAt(nextTab, i + n, hn);
                        // 旧的设置成占位符
                        setTabAt(tab, i, fwd);
                        // 继续向后推进
                        advance = true;
                    }
                }
            }
        }
    }
}
```

以上流程如下：

1. 根据CPU核数和集合length计算每个核一轮处理桶的个数，最小是16
2. 修改transferIndex标志位，每个线程领取完任务就减去多少，比如初始大小是transferIndex = table.length = 64，每个线程领取的桶个数是16，第一个线程领取完任务后transferIndex = 48，也就是说第二个线程这时进来是从第48个桶开始处理，再减去16，依次类推，这就是多线程协作处理的原理
3. 领取完任务之后就开始处理，如果桶为空就设置为ForwardingNode,如果不为空就加锁拷贝，拷贝完成之后也设置为ForwardingNode节点
4. 如果某个线程分配的桶处理完了之后，再去申请，发现transferIndex = 0，这个时候就说明所有的桶都领取完了，但是别的线程领取任务之后有没有处理完并不知道，该线程会将sizeCtl的值减1，然后判断是不是所有线程都退出了，如果还有线程在处理，就退出
5. 直到最后一个线程处理完，发现sizeCtl = rs<< RESIZE_STAMP_SHIFT，才会将旧数组干掉，用新数组覆盖，并且会重新设置sizeCtl为新数组的扩容点

以上过程总的来说分成两个部分：

- 分配任务部分：这部分其实很简单，就是把一个大的数组给切分，切分多个小份，然后每个线程处理其中每一小份，当然可能就只有1个或者几个线程在扩容，那就一轮一轮的处理，一轮处理一份
- 处理任务部分：复制部分主要有两点，第一点就是加锁，第二点就是处理完之后置为ForwardingNode

上面代码中有两处标注要说明的地方，这里解释一下：

**说明1**

```
if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {// 尝试将 sc -1. 表示这个线程结束帮助扩容了，将 sc 的低 16 位减一。
                if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)// 如果 sc - 2 不等于标识符左移 16 位。如果他们相等了，说明没有线程在帮助他们扩容了。也就是说，扩容结束了。
                    return;// 不相等，说明没结束，当前线程结束方法。
                finishing = advance = true;// 如果相等，扩容结束了，更新 finising 变量
                i = n; // 再次循环检查一下整张表
            }
```

这段代码其实在上面介绍扩容点的时候就提过，每个线程要进来协助扩容的时候就sizeCtl + 1,这里处理完之后，就sizeCtl - 1，第二个if判断就是上面提到的判断是不是所有的线程都退出了，如果还有线程在执行，那个条件就会成立，就直接return。最后一个线程才会把finishing设置为true，这个是整个扩容结束的标志。

**说明2**

```
for (Node<K,V> p = f.next; p != null; p = p.next) {
                                int b = p.hash & n;
                                if (b != runBit) {
                                    runBit = b;
                                    lastRun = p;
                                }
                            }
                            if (runBit == 0) {
                                ln = lastRun;
                                hn = null;
                            }
                            else {
                                hn = lastRun;
                                ln = null;
                            }
                            for (Node<K,V> p = f; p != lastRun; p = p.next) {
                                int ph = p.hash; K pk = p.key; V pv = p.val;
                                if ((ph & n) == 0)
                                    ln = new Node<K,V>(ph, pk, pv, ln);
                                else
                                    hn = new Node<K,V>(ph, pk, pv, hn);
                            }
                            setTabAt(nextTab, i, ln);
                            setTabAt(nextTab, i + n, hn);
                            setTabAt(tab, i, fwd);
                            advance = true;
                        }
```

这部分代码是链表拷贝的时候，这里的问题是为什么搞两个for循环，网上很多资料在写扩容的时候，很多都会提这么一段话：“如果拷贝的是链表，就先把链表分成两个反向链表，再插入到新数组的和旧数据组相同的位置和旧数组的位置加上旧数组长度的位置上”，这段话后半部分没说错，前半部分说对了一半，原因就是跟第一个for循环有关，如果没有第一个for循环，确实是构建两个反向链表。

这里假设一种情况，第一个for循环中链表的所有的节点的runbit = 0,这时ln就是链表的首节点，那第二个for缓存就不会执行，因为条件不满足，这个时候就不会构建反向链表，通过这个例子大家应该能明白为什么搞两个for循环，就是为了减少new Node的数量。

## 4、helpTransfer()方法

在putVal()方法中，如果发现数组正在扩容，就是执行这个方法，这里贴一下代码

```
final Node<K,V>[] helpTransfer(Node<K,V>[] tab, Node<K,V> f) {
        Node<K,V>[] nextTab; int sc;
        if (tab != null && (f instanceof ForwardingNode) &&
            (nextTab = ((ForwardingNode<K,V>)f).nextTable) != null) {
            int rs = resizeStamp(tab.length);
            while (nextTab == nextTable && table == tab &&
                   (sc = sizeCtl) < 0) {
                if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                    sc == rs + MAX_RESIZERS || transferIndex <= 0)
                    break;
                if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) {
                    transfer(tab, nextTab);
                    break;
                }
            }
            return nextTab;
        }
        return table;
    }
```

其实和最开始分析扩容点时候的代码基本上是一样的。

## 5、总结

　本篇文章分析了扩容部分，这部分的代码比较长，需要点耐心，看完扩容的代码之后，大家应该都可以回答概述中的几个问题，总的来说扩容的思想其实很简单，就是多线程协作扩容，复制的时候加锁，防止多线程同时处理。这里面CAS用的非常多，而且很多地方用的很巧妙，unsafe这个魔法类在并发里面使用的真是太频繁了。

## 6、参考

[ConcurrentHashMap原理分析(二)-扩容](https://www.cnblogs.com/gunduzi/p/13651664.html)

# 三、计数

## 1、概述

由于ConcurrentHashMap是一个高并发的集合，集合中增删就比较频繁，那计数就变成了一个问题，如果使用像AtomicInteger这样类型的变量来计数，虽然可以保证原子性，但是太多线程去竞争CAS，自旋也挺浪费时间的，所以ConcurrentHashMap使用了一种类似LongAddr的数据结构去计数，其实LongAddr是继承Striped64，有关于这个类的原理大家可以参考这篇文章:[并发之STRIPED64（累加器）和 LONGADDER](https://coolshell.me/articles/striped64-and-longadder-in-jdk.html),大家了解了这个类的原理，理解ConcurrentHashMap计数就没有一点压力了，因为两者在代码实现上基本一样。

## 2、ConcurrentHashMap计数原理

```
	private transient volatile long baseCount;

    private transient volatile CounterCell[] counterCells;

    @sun.misc.Contended static final class CounterCell {
        volatile long value;
        CounterCell(long x) { value = x; }
    }
```

ConcurrentHashMap就是依托上面三个东东进行计数的，那下面就详细解释一下这三个东东。

- baseCount：最基础的计数，比如只有一个线程put操作，只需要通过CAS修改baseCount就可以了。
- counterCells：这是一个数组，里面放着CounterCell对象，这个类里面就一个属性，其使用方法是，在高并发的时候，多个线程都要进行计数，每个线程有一个探针hash值，通过这个hash值定位到数组桶的位置，如果这个位置有值就通过CAS修改CounterCell的value（如果修改失败，就换一个再试）,如果没有，就创建一个CounterCell对象。
- 最后通过把桶中的所有对象的value值和baseCount求和得到总值，代码如下。

```
final long sumCount() {
        CounterCell[] as = counterCells; CounterCell a;
        //baseCount作为基础值
        long sum = baseCount;
        if (as != null) {
            //遍历数组
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    //对每个value累加
                    sum += a.value;
            }
        }
        return sum;
    }
```

通过上面的分析，相信大家已经了解了高并发计数的方法，在上面的介绍中提到一点，每个线程的探针hash值，大家先有个印象，一会分析代码的时候会使用这个，其实这个值很有趣。

## 3、addCount()方法

又到了这个方法，在[上篇文章](https://www.cnblogs.com/gunduzi/p/13651664.html)中分析扩容的时候也分析过这个方法，不过分析的是一部分，现在分析另一部分。

```
private final void addCount(long x, int check) {
        CounterCell[] as; long b, s;
        //如果数组还没有创建，就直接对baseCount进行CAS操作
        if ((as = counterCells) != null ||
            !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
            CounterCell a; long v; int m;
            //设置没有冲突标志位为true
            boolean uncontended = true;
            //第一个条件as == null成立说明，counterCells数组没有创建，而且通过CAS修改baseCount失败，说明有别的线程竞争CAS
            //a = as[ThreadLocalRandom.getProbe() & m]) == null,说明数组是创建了，但是通过探针hash定位的桶中没有对象
            //如果有对象，执行最后一个，进行CAS修改CounterCell对象，如果也失败了，就要进入下面的方法
            if (as == null || (m = as.length - 1) < 0 ||
                (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
                !(uncontended =
                  U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
                //进行更精确的处理
                fullAddCount(x, uncontended);
                return;
            }
            if (check <= 1)
                return;
            s = sumCount();
        }
        //省略部分代码
}
```

上面整体过程还是很好理解的，就是我在上面介绍计数原理中说的步骤，但是上面有一个地方需要注意下就是：

```
a = as[ThreadLocalRandom.getProbe() & m]) == null 
```

这里的ThreadLocalRandom.getProbe()，看着名字大家应该可以猜到应该是要获取一个随机值，因为有Random嘛，其实这里就是在获取一个随机值。那既然是要获取一个随机值，为什么要使用ThreadLocalRandom，而不直接使用Random，那看到ThreadLocal，大家也应该可以想到就是这个随机值的获取，线程之间是隔离的，每个线程获取自己的随机值，互相之间没有影响，为什么要互相之间没有影响呢？因为Random要实现随机，有一个关键的东西就是种子（seed），具体过程如下：

- 初始化Random的时候，如果没有传seed，就根据时间戳进行一些计算初始化一个种子
- 如果某个线程需要获取随机数，先通过CAS更新种子seed1 = function1(seed)
- 根据seed1，计算随机数 = function2(seed1)
- 上面的两个function是固定的，就是说如果初始种子一样，两个不同的Random对象生成随机数会完全一样

上面的过程咋一看没啥问题，其实最大问题就是第二步那个CAS，在高并发的时候效率会很差，所以这里才使用了ThreadLocalRandom，相当于每个线程都有一个Random，都有自己的种子，这样就不会存在多线程竞争修改种子。想要详细了解ThreadLocalRandom,参考：[并发包中ThreadLocalRandom类原理剖析](https://www.jianshu.com/p/9c2198586f9b)

## 4、fullAddCount()方法

其实这个方法没什么好分析的，其实就是Striped64#longAccumulate()方法，据我的观察好像一行不差，完全一样，这里还是分析下吧。

```
private final void fullAddCount(long x, boolean wasUncontended) {
        int h;
         //上面我贴出来了介绍ThreadLocalRandom的文章，这里如果是首次获取，其实就是0
         if ((h = ThreadLocalRandom.getProbe()) == 0) {
            //如果为0，就初始化，这里其实就是把种子和随机数设置到（Thread）线程中
            ThreadLocalRandom.localInit();      // force initialization
            h = ThreadLocalRandom.getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty

        //死循环，保证计数一定成功
        for (;;) {
            CounterCell[] as; CounterCell a; int n; long v;
            //说明数组已经初始化，在后面有判断数组没有初始化的情况
            if ((as = counterCells) != null && (n = as.length) > 0) {
                //这里是不是和ConcurrentHashMap定位桶的位置很像，其实是一摸一样的
               //说明数组中这个位置没有元素
               if ((a = as[(n - 1) & h]) == null) {
                    //这个字段保证数组新增节点，扩容只有一个线程在进行，防止多线程并发
                    //这里限制一个线程处理只是在数组新增节点和扩容的时候，修改对象的值并不需要限制这个变量
                    if (cellsBusy == 0) {            // Try to attach new Cell
                        CounterCell r = new CounterCell(x); // Optimistic create

                        //如果为0表示没有别的线程在修改数组，通过CAS修改为1，表示当前线程在修改数组
                        if (cellsBusy == 0 &&
                            U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                            boolean created = false;
                            try {               // Recheck under lock
                                CounterCell[] rs; int m, j;
                                //再次校验，确保数组没有变化
                                //rs[j = (m - 1) & h] == null，再次确认该位置是否为null，防止别的线程插入了
                                if ((rs = counterCells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    //插入数组
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                               //释放CAS锁
                                cellsBusy = 0;
                            }
                            if (created)
                                //如果新节点插入成功，表示计数已经成功，这里直接break了
                                break;
                           //如果失败会一直重试
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                }
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash

                //定位到桶中有值，然后通过CAS修改其值
                else if (U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))
                    break;
               //下面的两个elseif其实是为了防止数组一直扩容使用的，数组的最大容量就是CPU的核数
               //因为核数就是并发数，数组太大没有意义，没有那么多线程可以同时操作
               //就是说上面的新建节点或者CAS修改值事变了，就会到这里，然后拦截住，不让执行扩容
                else if (counterCells != as || n >= NCPU)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                //先竞争到CAS锁，然后执行扩容
                else if (cellsBusy == 0 &&
                         U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                    try {
                        if (counterCells == as) {// Expand table unless stale

                            //每次扩容成原来的两倍
                            CounterCell[] rs = new CounterCell[n << 1];
                            //复制元素，看过ConcurrentHashMap的扩容，再看这个，简直就跟一个大学生看小学数学题一样，😄
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            counterCells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                //这里是重新生成一个随机数，换个位置试试，比如上面新增节点失败了，换个位置试试，或者通过CAS修改值失败，也换个位置再试试
                h = ThreadLocalRandom.advanceProbe(h);
            }
            //这里就是判断数组没有初始化的情况，搞不明白没啥放在这里，不放在开头
            else if (cellsBusy == 0 && counterCells == as &&
                     U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                boolean init = false;
                try {                           // Initialize table
                    if (counterCells == as) {
                        //初始化的数组大小是2，非常小
                        CounterCell[] rs = new CounterCell[2];
                        rs[h & 1] = new CounterCell(x);
                        counterCells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            }
            //如果以上CAS修改，创建新节点都失败了，这里还有一道防线，通过CAS修改baseCount
            //这也是再addCount中，当判断数组不为空，不先修改下baseCount试试，而是直接跳到这个方法中，因为在这个方法中也会修改baseCount
            else if (U.compareAndSwapLong(this, BASECOUNT, v = baseCount, v + x))
                break;                          // Fall back on using base
        }
    }
```

方法流程梳理如下：

1. 判断数组是否为空，为空的话初始化数组
2. 如果数组存在，通过探针hash定位桶中的位置，如果桶中为空，新建节点，通过CAS锁插入数组，如果成功，结束，如果失败转到第5步
3. 如果定位到桶中有值，通过CAS修改，如果成功，结束，如果失败向下走
4. 如果数组大小小于CPU核数，扩容数组
5. 重新计算探针hash

## 5、总结

计数的原理就是概述中说的使用的是striped64，为啥不直接继承striped64，不太懂，可能striped64出来晚一点，里面使用到ThreadLocalRandom，这个其实还是挺有意思的，总的来说计数过程并不复杂，是看ConcurrentHashMap源码的时候比较愉快的部分。

## 6、参考

[ConcurrentHashMap原理分析(三)-计数](https://www.cnblogs.com/gunduzi/p/13653505.html)











## END