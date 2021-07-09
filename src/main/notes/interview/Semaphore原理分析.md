# Semaphore原理分析

## 一、概述

信号量Semaphore是一个控制访问多个共享资源的计数器，和CountDownLatch一样，本质上是一种共享锁。举个例子，还是生产者消费者的例子，假设缓冲区的大小是100，然后可以实现多个生产者和消费者同时进行工作，只要100个资源没有使用完，生产者就可以继续生产，而在之前[一篇文章](https://www.cnblogs.com/gunduzi/p/13614429.html)分析Condition的时候也举了这个例子，那里是使用ReentrantLock + Condition组合实现生产者消费者模型，一次只能有一个生产者或者消费者进行生产或者消费，而使用Semaphore就可以实现多个生产者消费者同时一起工作。

## 二、Semaphore类结构

　![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200904223333601-1229658848.png)

 这个实现结构和ReentrantLock一样，分析如下：

- Semaphore内部是基于Sync这个抽象类实现的，而Sync继承了AQS，重写了部分AQS的方法
- Sync有两个继承类，FairSync是实现公平的获取资源，而NonFairSync是实现非公平获取资源

## 三、Semaphore构造方法

```
//不传参数，默认是非公平方式
public Semaphore(int permits) {
        sync = new NonfairSync(permits);
    }
//通过参数控制公平的方式还是非公平
 public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }
```

## 四、Sync抽象类分析

```
abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1192457210091910933L;

        Sync(int permits) {
            setState(permits);
        }
　　　　　//获取剩余资源数量
        final int getPermits() {
            return getState();
        }
        //非公平获取资源，采用死循环，如有剩余资源，一直通过CAS加锁，直到成功为止
        final int nonfairTryAcquireShared(int acquires) {
            for (;;) {
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }
        //释放锁
        protected final boolean tryReleaseShared(int releases) {
            for (;;) {
                int current = getState();
                int next = current + releases;
                if (next < current) // overflow
                    throw new Error("Maximum permit count exceeded");
                if (compareAndSetState(current, next))
                    return true;
            }
        }
        //减少可用资源数量
        final void reducePermits(int reductions) {
            for (;;) {
                int current = getState();
                int next = current - reductions;
                if (next > current) // underflow
                    throw new Error("Permit count underflow");
                if (compareAndSetState(current, next))
                    return;
            }
        }
        //将可用资源数量设置为0
        final int drainPermits() {
            for (;;) {
                int current = getState();
                if (current == 0 || compareAndSetState(current, 0))
                    return current;
            }
        }
    }
```

## 五、NonFairSync类分析

```
static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -2694183684443567898L;

        NonfairSync(int permits) {
            super(permits);
        }
        //尝试获取共享锁，调用上面Sync中的方法
        protected int tryAcquireShared(int acquires) {
            return nonfairTryAcquireShared(acquires);
        }
    }
```

## 六、FairSync类分析

```
static final class FairSync extends Sync {
        private static final long serialVersionUID = 2014338818796000944L;

        FairSync(int permits) {
            super(permits);
        }
        //公平的方式获取共享资源，和非公平的方式最主要的区别就是多了一个hasQueuedPredecessors方法，下面会分析这个方法
        protected int tryAcquireShared(int acquires) {
            for (;;) {
                if (hasQueuedPredecessors())
                    return -1;
                //剩下的和非公平锁一样
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }
    }
```

进入AbstractQueuedSynchronizer #hasQueuedPredecessors()

```
public final boolean hasQueuedPredecessors() {
   
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;
    //该方法和ReentrantLock实现公平锁时调用是同一个方法
    //当前队列中不只有一个节点
    //判断第二个节点是否为空，这个主要是为了防止第三个条件发生空指针异常，因为第一个条件虽然判断了队列中有第二个节点
    //但是由于是在多线程环境下，随时都有可能第一个节点被干掉，第二个节点变成第一个节点，然后第二个就是null了，再之后就会发生空
    //指针异常，这个返回的主要是为了判断当前线程是不是队列中的第二个节点，因为队列的第一个节点是虚拟节点，第二个节点才有获取锁的资格
    //在公平锁的情况下是这样，但是非公平锁就不是这样了
    return h != t && ((s = h.next) == null || s.thread != Thread.currentThread()); }
```

## 七、Semaphore获取资源方法

#### 对中断敏感的方法

```
   public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
```

这个就是调用上面实现的公平或者非公平实现类中获取资源类中的方法，该方法对中断敏感，就是当当前线程挂起的时候，发生了中断，当再次唤醒的时候就会抛出异常。

#### 对中断不敏感的方法

```
 public void acquireUninterruptibly() {
        sync.acquireShared(1);
    }
```

## 八、Semaphore释放资源方法

```
    public void release() {
        sync.releaseShared(1);
    }
```

## 九、其他方法

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
//尝试获取资源，如果没有可用资源直接返回，如果有资源无限重试获取资源
public boolean tryAcquire() {
        return sync.nonfairTryAcquireShared(1) >= 0;
    }
//尝试获取资源，如果没有可用资源，阻塞，在规定时间内没有获取到资源，就取消请求资源
 public boolean tryAcquire(long timeout, TimeUnit unit)
        throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }
//判断阻塞队列中是否有等待的线程
public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }
```

## 十、总结

整体来说，Semaphore的实现和ReentrantLock非常像，前者使用AQS的共享模式，后者使用AQS的独占模式，在文章的开头有贴出AQS共享模式的分析文章，所以本文中有关调用AQS共享模式的方法就没有重复分析，查看上一篇文章就可以了。





## 参考

[Semaphore原理分析](https://www.cnblogs.com/gunduzi/p/13616634.html)



