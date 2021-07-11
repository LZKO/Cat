# LinkedBlockingQueue原理分析

## 一、概述

LinkedBlockingQueue也是一个阻塞队列，相比于ArrayBlockingQueue，他的底层是使用链表实现的，而且是一个可有界可无界的队列，在生产和消费的时候使用了两把锁，提高并发，是一个高效的阻塞队列，下面就分析一下这个队列的源码。

## 二、属性

```
//链表节点定义
static class Node<E> {
        //节点中存放的值
        E item;
        //下一个节点
        Node<E> next;
 
        Node(E x) { item = x; }
    }
    //容量
    private final int capacity;
    //队列中元素个数
    private final AtomicInteger count = new AtomicInteger();
    //队列的首节点
    transient Node<E> head;
    //队列的未节点
    private transient Node<E> last;
 
    /** Lock held by take, poll, etc */
    //消费者的锁
    private final ReentrantLock takeLock = new ReentrantLock();
 
    /** Wait queue for waiting takes */
    private final Condition notEmpty = takeLock.newCondition();
 
    /** Lock held by put, offer, etc */
   //生产者的锁
    private final ReentrantLock putLock = new ReentrantLock();
 
    /** Wait queue for waiting puts */
    private final Condition notFull = putLock.newCondition();
```

## 三、构造方法

```
//默认构造方法，无界
public LinkedBlockingQueue() {
    this(Integer.MAX_VALUE);
}
//可以传入容量大小，有界
public LinkedBlockingQueue(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException();
    this.capacity = capacity;
    last = head = new Node<E>(null);
}
```

## 四、消费者常用方法

#### take()方法

```
 public E take() throws InterruptedException {
        E x;
        int c = -1;
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        //获取可中断锁
        takeLock.lockInterruptibly();
        try {
           //如果队列为空
            while (count.get() == 0) {
                notEmpty.await();
            }
            //执行消费
            x = dequeue();
            //先赋值，后自减
            c = count.getAndDecrement();
            if (c > 1)
               //如果队列中还有值，唤醒别的消费者
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        //队列中还有要给剩余空间
        if (c == capacity)
            //唤醒生产者线程
            signalNotFull();
        return x;
    }
```

进入dequeue()方法

```
//通过这个方法可以看出，链表的首节点的值是null，每次获取元素的时候
//先把首节点干掉，然后从第二个节点获取值
private E dequeue() {
        Node<E> h = head;
        Node<E> first = h.next;
        h.next = h; // help GC
        head = first;
        E x = first.item;
        first.item = null;
        return x;
    }
```

#### poll()方法

```
public E poll() {
        final AtomicInteger count = this.count;
        if (count.get() == 0)
            return null;
        E x = null;
        int c = -1;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
             //如果队列不为空
            if (count.get() > 0) {
                x = dequeue();
                c = count.getAndDecrement();
                if (c > 1)
                    notEmpty.signal();
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }
```

#### poll(long timeout, TimeUnit unit)

　　这个方法和上面的区别就是加入了时延，在规定的时间没有消费成功，就返回失败。

## 五、生产者常用方法

#### add()方法

```
public boolean add(E e) {
    if (offer(e))
        return true;
    else
        throw new IllegalStateException("Queue full");
}
```

直接调用父类AbstractQueue的方法

#### offer(E e)方法

```
public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        final AtomicInteger count = this.count;
        //如果已经满了，直接返回失败
        if (count.get() == capacity)
            return false;
        int c = -1;
        Node<E> node = new Node<E>(e);
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            //双重判断
            if (count.get() < capacity) {
                //加入链表
                enqueue(node);
                c = count.getAndIncrement();
                if (c + 1 < capacity)
                    //唤醒生产者线程，继续插入
                    notFull.signal();
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0)
            //说明里面有一个元素，唤醒消费者
            signalNotEmpty();
        return c >= 0;
    }
```

进入enqueue()方法

```
private void enqueue(Node<E> node) {
    // assert putLock.isHeldByCurrentThread();
    // assert last.next == null;
    last = last.next = node;
}
```

直接放到链表的尾部

#### offer(E e, long timeout, TimeUnit unit)

和poll(E e,long timeout,TimeUnit unit)相反。

#### put(E e)方法

```
public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        // Note: convention in all put/take/etc is to preset local var
        // holding count negative to indicate failure unless set.
        int c = -1;
        Node<E> node = new Node<E>(e);
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();
        try {
            //如果满了，等待
            while (count.get() == capacity) {
                notFull.await();
            }
            enqueue(node);
            c = count.getAndIncrement();
            if (c + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
    }
```

## 六、总结

总体来说比较简单，下面就列一下LindedBlockingQueue的特点：

- 生产者和消费者采用不同的锁控制，提高并发效率
- 底层采用链表存储，构造方法中可以传入队列的容量，默认为无界
- 链表的首节点是一个空节点

## 七、参考

[LinkedBlockingQueue原理分析](https://www.cnblogs.com/gunduzi/p/13665704.html)



## END