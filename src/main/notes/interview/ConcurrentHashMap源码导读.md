# ConcurrentHashMap源码导读

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









## END