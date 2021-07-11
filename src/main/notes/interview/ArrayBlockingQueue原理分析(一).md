# ArrayBlockingQueue原理分析(一)

## 一、概述

ArrayBlockingQueue是一个阻塞队列，其实底层就是一个数组，说到底层是数组，ArrayList底层也是数组，那它其实也可以作为队列，但是是非阻塞的，那阻塞和非阻塞的区别是什么？区别在于当队列中没有元素的时候就阻塞等待，直到队列中有数据再消费，而如果队列满了之后（队列有界），生产者就要阻塞。下面就总结一下ArrayBlockingQueue的特性。

- 是一个有界的队列，初始化队列的时候传入队列大小
- 采用ReentrantLock + Condition实现线程安全和阻塞
- 底层采用数组存储
- 生产者和消费者共用一把锁，所以效率一般

总结来说就是效率一般，容量有限，那既然这么差还要搞一个这个对象，原因就是这个实现起来简单。ArrayBlockingQueue的插入和删除操作都比较简单，但是里面有一个东西其实还挺复杂的，就是Itrs,迭代器，我打算写两篇博客，本篇介绍插入和删除等一般的方法，下一篇介绍迭代器。

## 二、继承结构图

![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200912230137551-924443890.png)

 这幅图画出了Java中常用队列的继承结构图，可以看出所有的队列都实现了AbstartQueue，这个抽象类实现了Queue接口，提供了Queue接口中方法的默认实现，继承它可以少写一些不必要的代码，但是Queue接口中没有提供大多数的阻塞方法，所以有了BlockingQueue接口，这个接口中提供很多的阻塞的插入删除方法，而最底层的实现者都是阻塞队列，所以都会实现这个接口。

## 三、属性分析

```
 /** 队列中元素保存的地方 */
    final Object[] items;

    /** items index for next take, poll, peek or remove，这个英文注释很详细 */
    int takeIndex;

    /** items index for next put, offer, or add */
    int putIndex;

    /** Number of elements in the queue */
    int count;

    /** Main lock guarding all access */
    final ReentrantLock lock;

    /** Condition for waiting takes，处理消费者线程的 */
    private final Condition notEmpty;

    /** Condition for waiting puts，处理生产者线程的 */
    private final Condition notFull;

    /**
     * Shared state for currently active iterators, or null if there
     * are known not to be any.  Allows queue operations to update
     * iterator state.     * 迭代器，由于一个队列可以有多个迭代器，所以在该队列中，迭代器通过链表连接起来，itrs属性就是链表头     * 通过这个头，就可以找到所有的迭代器，下一篇文章会具体分析
     */
    transient Itrs itrs = null;
```

## 四、构造方法

```
public ArrayBlockingQueue(int capacity) {
        this(capacity, false);
    }

public ArrayBlockingQueue(int capacity, boolean fair) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        //初始化数组，指定容量，不会扩容，固定
        this.items = new Object[capacity];
        //默认是非公平锁，下面会解释一下这个
        lock = new ReentrantLock(fair);
        notEmpty = lock.newCondition();
        notFull =  lock.newCondition();
    }
```

关于这里的公平锁，在网上看资料时，发现有的胖友写的文章中说，这里如果是公平锁，如果队列满了，生产者阻塞了非常多，那等待最久的生产者线程会先竞争到锁，别的阻塞线程不能跟他竞争锁，其实这么解释的结论是对的，就是如果是公平锁，那等最久的确实先竞争到锁，但是原因并不是上面说的原因，即便是非公平锁，如果没有新的生产者加入竞争锁，也是等最久先竞争到锁，这里公平锁的作用是说，当多个消费者线程消费多个元素之后，唤醒多个生产者，这时候如果有新的生产者加入，这些生产者需要等待刚刚唤醒的生产者执行完之后才可以竞争锁。

## 五、Queue

```
public interface Queue<E> extends Collection<E> {
    //添加元素
    boolean add(E e);
    //添加元素
    boolean offer(E e);
    //删除
    E remove();
    //消费元素，其实也是删除
    E poll();
    //当前元素
    E element();
    //当前消费要消费的元素，不会移除队列，只是获取
    E peek();
}
```

## 六、BlockingQueue

```
//其实这个接口继承于Queue，只不过多添加了几个方法
public interface BlockingQueue<E> extends Queue<E> {
    //添加元素
    boolean add(E e);
    //添加元素
    boolean offer(E e);
    //添加元素
    void put(E e) throws InterruptedException;
    //添加元素
    boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException;
    //消费元素
    E take() throws InterruptedException;
    //消费元素
    E poll(long timeout, TimeUnit unit)
        throws InterruptedException;
    
    int remainingCapacity();
    //删除元素
    boolean remove(Object o);

    public boolean contains(Object o);

    int drainTo(Collection<? super E> c);

    public boolean contains(Object o);

}
```

搞不懂有些方法在Queue中已经定义了，这里还要重复定义。

## 七、ArrayBlockedQueue

ArrayBlockedQueue实现了上面的队列，下面就对其实现的方法做一个对比。

| 生产者方法                                                   | 消费者方法                                                   |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| add(E e)：直接调用offer,和offer方法返回不同，如果插入成功，返回true，否则抛出异常 |                                                              |
| offer(E e)：向队尾插入数据，如果队列满了，就返回插入失败     | poll()：从队头获取元素，如果队列为空，返回失败               |
| offer(E e, long timeout, TimeUnit unit)：向队尾插入数据，如果队列满了，阻塞等待一段时间，超时返回插入失败 | poll(long timeout, TimeUnit unit)：从队头获取元素，如果队列为空，等待一段时间，如果超时返回失败 |
| put(E e)：向队尾插入元素，如果队列满了就一直等待，直到队列中有空闲空间 | take()：从队头获取元素，如果队列为空，阻塞等待，直到队列中有元素再消费 |
|                                                              | remove(Object o)：删除队列中的元素，可以是队列中的任意元素   |

从上面的对比可知，中间三个方法生产者和消费者是相对的。

#### add(E e)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
public boolean add(E e) {
        return super.add(e);
    }

//AbstractQueue的实现，直接调用offer方法，不过和offer不同的是，如果插入队列失败
//直接抛出异常
public boolean add(E e) {
        if (offer(e))
            return true;
        else
            throw new IllegalStateException("Queue full");
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

#### offer(E e)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
    public boolean offer(E e) {
        checkNotNull(e);
        final ReentrantLock lock = this.lock;
        //先加锁，防止多线程出问题
        lock.lock();
        try {
            //如果数组满了，返回false
            if (count == items.length)
                return false;
            else {
                //下面分析这个方法
                enqueue(e);
                return true;
            }
        } finally {
            lock.unlock();
        }
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

进入#enqueue(E e)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
    private void enqueue(E x) {
        // assert lock.getHoldCount() == 1;
        // assert items[putIndex] == null;
        final Object[] items = this.items;
        //队尾插入
        items[putIndex] = x;
        if (++putIndex == items.length)
            putIndex = 0;
        //元素数量自增
        count++;
        //通知阻塞的消费者线程
        notEmpty.signal();
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

这个方法实现很简单，不过生产者的几个方法基本都是调用这个方法。

#### offer(E e, long timeout, TimeUnit unit)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {

        checkNotNull(e);
        //获取传入的超时时间，转为纳秒
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        //获取可中断锁
        lock.lockInterruptibly();
        try {
            //如果队列满了，进入循环
            while (count == items.length) {
               //下面那个返回负数，这里直接return一个false结束
                if (nanos <= 0)
                    return false;
                //阻塞固定的时间，如果超时之后会返回一个0或者负数
                nanos = notFull.awaitNanos(nanos);
            }
            //还是调用这个方法，就不分析了
            enqueue(e);
            return true;
        } finally {
            lock.unlock();
        }
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

这个方法和offer(E e)有两点不同：

- 使用的锁是可中断锁，就是说如果在等待过程中，线程被中断了会抛出一个异常
- 使用了超时等待，如果超时才会返回false

#### put(E e)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
    public void put(E e) throws InterruptedException {
        checkNotNull(e);
        final ReentrantLock lock = this.lock;
        //可中断锁
        lock.lockInterruptibly();
        try {
           //队列满了
            while (count == items.length)
                //等待，不设置超时时间
                notFull.await();
            enqueue(e);
        } finally {
            lock.unlock();
        }
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

ok，到此就把生产者的方法分析完了，其实都是调用的enqueue方法，很简单。

用最帅的陈永仁做分割线。。。

　　　　　　![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200913000357501-1863519936.png)

#### poll(E e)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            //如果队列为空返回null，否则之后后面的方法
            return (count == 0) ? null : dequeue();
        } finally {
            lock.unlock();
        }
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

进入dequeue()方法

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
private E dequeue() {
        // assert lock.getHoldCount() == 1;
        // assert items[takeIndex] != null;
        final Object[] items = this.items;
        @SuppressWarnings("unchecked")
       //从队列中获取
        E x = (E) items[takeIndex];
        //删除这个元素
        items[takeIndex] = null;
        //判断是不是到队尾了，如果到队尾了就从头开始
        if (++takeIndex == items.length)
            takeIndex = 0;
        count--;
        //如果该队列存在迭代器，更新里面一些信息，后面会稍微说明一下
        if (itrs != null)
            itrs.elementDequeued();
        //通知生产者线程
        notFull.signal();
        return x;
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

上面的过程都非常简单，这里提一下itrs这部分代码，举个例子，假设通过如下代码获取了一个迭代器

```
ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue(10);
Iterator<Integer> iterator = queue.iterator();
```

在迭代器初始化时候，其第一个要迭代的元素和消费者要消费的元素是同一个，在上面的代码中如果消费者消费元素，把队列中所有的元素消费完了，但是迭代器还没有运行，这个时候就需要更新迭代器中的一些参数，不让它迭代了，因为队列已经为空了，这里只是提一下，下一篇文章会详细介绍。

#### poll(long timeout, TimeUnit unit)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                if (nanos <= 0)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            return dequeue();
        } finally {
            lock.unlock();
        }
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

#### take(E e)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == 0)
                notEmpty.await();
            return dequeue();
        } finally {
            lock.unlock();
        }
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

#### remove(Object o)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
public boolean remove(Object o) {
        if (o == null) return false;
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count > 0) {
                final int putIndex = this.putIndex;
                int i = takeIndex;
                do {
                   //遍历队列，寻找要删除的元素
                    if (o.equals(items[i])) {
                        //执行删除，之后移动数组中的元素，把删除的元素的空位置给挤占掉
                        removeAt(i);
                        return true;
                    }
                    if (++i == items.length)
                        i = 0;
                } while (i != putIndex);
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

进入removeAt(i)

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
void removeAt(final int removeIndex) {
        // assert lock.getHoldCount() == 1;
        // assert items[removeIndex] != null;
        // assert removeIndex >= 0 && removeIndex < items.length;
        final Object[] items = this.items;
        //如果删除的就是下一个要消费的元素
        if (removeIndex == takeIndex) {
            // removing front item; just advance
            items[takeIndex] = null;
            if (++takeIndex == items.length)
                takeIndex = 0;
            count--;
            if (itrs != null)
                itrs.elementDequeued();
        } else {
            // an "interior" remove

            // slide over all others up through putIndex.
            final int putIndex = this.putIndex;
            //移动元素
            for (int i = removeIndex;;) {
                int next = i + 1;
                if (next == items.length)
                    next = 0;
                if (next != putIndex) {
                    items[i] = items[next];
                    i = next;
                } else {
                    items[i] = null;
                    this.putIndex = i;
                    break;
                }
            }
            count--;
            if (itrs != null)
                //下一篇文章分析
                itrs.removedAt(removeIndex);
        }
        //通知生产者线程
        notFull.signal();
    }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

以上就是ArrayBlockingQueue常用方法，还有几个比如contains,toString,toArray都很简单，就不贴代码了。



## 八、总结

ArrayBlockingQueue总的来说在上面列的结构图中算是最简单的一个，在概述中也说了，其实这里面复杂的是迭代，队列其实很简单，下一篇文章分析迭代过程。

​               ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200912235342045-156069159.png)



## 九、参考

[ArrayBlockingQueue原理分析(一)](https://www.cnblogs.com/gunduzi/p/13659370.html)



## END



