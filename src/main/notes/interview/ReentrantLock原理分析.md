# ReentrantLock原理分析

## 一、概述

ReentrantLock是基于AQS独占模式实现的一种可重入锁，与synchronized不同的是，ReentrantLock提供了公平锁和非公平锁的选择。其本质是基于操作系统的管程实现的。本文就分析一下ReentrantLock的实现原理，由于AQS在[AQS-独占模式分析](https://www.cnblogs.com/gunduzi/p/13608203.html)已经介绍过，所以涉及到AQS的地方不会过多介绍，本文会涉及到源码，文章会比较臃肿。

## 二、ReentrantLock整体结构

   ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200904172839684-824432671.png)

 从上图可以看出：

- ReentrantLock实现了Lock接口
- ReentrantLock内部是基于Sync这个抽象类实现的，而Sync继承了AQS，重写了部分AQS的方法
- Sync有两个继承类，FairSync是实现公平锁的，而NonFairSync是实现非公平锁的

下面就分析一下每一块的代码

## 三、Sync抽象类

```
//继承AQS
abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;
　　　　　//子类实现加锁方法，因为有公平锁和非公平锁，所以这里没有实现
        abstract void lock();
　　　　　//该方法时tryLock方法调用的，tryLock方法是尝试获取锁，如果没有获取成功，就直接返回
        //不会阻塞，非公平的方式，直接竞争锁
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            //该state是AQS中的，如果为0表示没有别的线程占有锁
            if (c == 0) {
                //通过CAS加锁
                if (compareAndSetState(0, acquires)) {
                    //加锁成功，将持有锁的线程改成当前线程
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            //如果持有锁的线程就是当前线程，就是可重入锁，直接修改状态
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            //否则加锁失败，直接返回
            return false;
        }
　　　　　//释放锁
        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }
        //判断当前线程是不是持有锁的线程
        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }
        //在AQS中的创建ConditionObject对象，保存等待线程
        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }
```

Sync抽象类总结

- 该类主要实现了一个非公平的方式尝试获取锁，如果成功，ok，如果失败，直接返回，不会阻塞或者空转等待
- 实现了一个释放锁的方法，该方法是公平锁和非公平锁公用的方法
- 剩下的都是一些判断的方法，很简单　

## 四、NonFairSync类分析

```
static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;
        //实现加锁方法，ReentrantLock默认就是这个方法，是一个非公平锁
        final void lock() {
            if (compareAndSetState(0, 1))
                setExclusiveOwnerThread(Thread.currentThread());
            else
                acquire(1);
        }
        //这里就是调用上面Sync类中尝试获取锁的方法
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }
```

## 五、FairSync类分析

```
static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;
        //公平锁获取锁的实现和上面就不一样，这里没有通过CAS尝试获取锁，而是直接调用acquire方法
        final void lock() {
            acquire(1);
        }
        //尝试获取锁
        protected final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                //这里就是和非公平锁不同的地方，这里调用了hasQueuedPredecessors，后面会分析这个方法
                //剩下的和公平锁的实现就是一样的
                if (!hasQueuedPredecessors() &&
                    compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }
    }
```

进入AbstractQueuedSynchronizer #hasQueuedPredecessors()方法

```
public final boolean hasQueuedPredecessors() {
         Node t = tail; // Read fields in reverse initialization order
         Node h = head;
         Node s;
          //当前队列中不只有一个节点
          //判断第二个节点是否为空，这个主要是为了防止第三个条件发生空指针异常，因为第一个条件虽然判断了队列中有第二个节点
          //但是由于是在多线程环境下，随时都有可能第一个节点被干掉，第二个节点变成第一个节点，然后第二个就是null了，再之后就会发生空
          //指针异常，这个返回的主要是为了判断当前线程是不是队列中的第二个节点，因为队列的第一个节点是虚拟节点，第二个节点才有获取锁的资格
          //在公平锁的情况下是这样，但是非公平锁就不是这样了
         return h != t &&
             ((s = h.next) == null || s.thread != Thread.currentThread());
     }
```

总结：公平锁的实现就是严格按照入队的先后顺序获取锁。

## 六、Lock接口

```java
public interface Lock {

    void lock();
    //获取可中断锁
    void lockInterruptibly() throws InterruptedException;
    //尝试获取锁
    boolean tryLock();
    //try获取锁，设置锁最大等待时间，如果超时就取消获取
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;
   
    void unlock();

    Condition newCondition();
}
```

## 七、ReentrantLock构造方法

```
public ReentrantLock() {
        sync = new NonfairSync();
    }
//根据传入参数不同，初始化不同的类
public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }
```

## 八、ReentrantLock和Synchronized对比

- 与 `synchronized` 相比，ReentrantLock提供了更多，更加全面的功能，具备更强的扩展性。例如：时间锁等候，可中断锁等候
- ReentrantLock 还提供了条件 Condition ，对线程的等待、唤醒操作更加详细和灵活，所以在多个条件变量和高度竞争锁的地方，ReentrantLock 更加适合。
- ReentrantLock 支持中断处理，且性能较 `synchronized` 会好些。
- ReentrantLock实现了公平锁和非公平锁的机制，而synchronized只有非公平一种模式
- synchronized不需要手动释放锁，但是ReentrantLock释放锁的时候，需要程序员自己释放
- ReentrantLock和synchronized都是通过内存屏障来保证有序性、可见性的，只不过ReentrantLock是volatile实现的，而synchronized不是使用volatile，但是volatile和synchronized在保证有序性和可见性上使用的内存屏障是一样的，所以也就是说两者基本相同

## 九、总结

本文介绍ReentrantLock源码结构以及实现原理，涉及到AQS的部分没有详细说明，主要是另一个篇文章已经介绍过，最后介绍了ReentrantLock和sychronized的区别。

## 十、参考

[ReentrantLock原理分析](https://www.cnblogs.com/gunduzi/p/13615441.html)

## END