# AQS-Condition介绍

## 一、概述

　Condition的作用用一句话概括就是为了实现线程的等待（await）和唤醒（signal），多线程情况下为什么需要等待唤醒机制？原因是有些线程执行到某个阶段需要等待符合某个条件才可以继续执行，在之前学习操作系统的时候，有一个经典的场景就是在容量有限的缓冲区实现生产者消费者模型，如果缓冲区满了，这个时候生产者就不能再生产了，就要阻塞等待消费者消费，当缓冲区为空了，消费者就要阻塞等待生产者生产，这就是一个很典型的使用condition实现条件状态的场景。那本文就介绍一下AQS中的Condition实现原理，本文会涉及源码，介绍完原理之后，会和对象的wait/notify机制做一个对比。

## 二、Condition使用例子

```
public class Test {
 
final ReentrantLock lock = new ReentrantLock();
final Condition condition = lock.newCondition();
 
    public void awaitTest() throws Exception{
        try {
            lock.lock();
            condition.await();
            System.out.println("解除等待");
        } finally {
            lock.unlock();
        }
    }
    public void signalTest(){
        try {
            lock.lock();
            condition.signal();
            System.out.println("继续执行");
        } finally {
            lock.unlock();
        }
    }
 
public static void main(String[] args) {
        Test test = new Test();
        new Thread(()->{
            try {
                test.awaitTest();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
 
        new Thread(()->{
            test.signalTest();
        }).start();
    }
}
```

执行结果如下：

```
继续执行
解除等待
```

上面的代码是一个Condition最简单的使用方法，第一个方法await，第二个方法signal，执行完signal之后并不是把当前线程挂起去执行await方法，而是把当前方法执行完之后，释放锁之后，执行awaitTest的线程再去获取到锁，继续执行。

## 三、Condition原理介绍

了解AQS原理的都知道，AQS有一个阻塞队列，把没有获取到锁的线程都放到这个队列中，但AQS中其实还有别的队列，那就是等待队列，就是放执行await之后的线程，大家看上面的例子可以发现，执行了这么一段代码：

```
final Condition condition = lock.newCondition();
```

这里是new了一个Condition，这段代码就会在AQS中创建一个等待队列，那如果多次执行上面的代码，就会在AQS中创建多个等待队列。

如下图所示：

![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200904144323653-1468231329.png)

#### AQS中await/signal原理

当线程执行await，意味着当前线程一定是持有锁的，首先会把当前线程放入到等待队列队尾，之后把当前线程的锁释放掉，在[上一篇文章](https://www.cnblogs.com/gunduzi/p/13608203.html)介绍中可知，当当前线程释放锁之后，阻塞队列的第二个节点会获取到锁（正常情况下），当前持有锁的节点是首节点，当释放锁之后，首节点会被干掉，也就是说执行await的线程会从阻塞队列中干掉。

当执行signal的时候，会把位于等待队列中的首节点（首节点是等待时间最长的，因为是从队尾入队的）线程给唤醒，注意这里唤醒之后该线程并不能立即获取到锁，而是会把这个线程加入到阻塞队列队尾，如果阻塞队列中有很多的线程在等待，那被唤醒的线程还会继续挂起，然后慢慢等待去获取锁。

## 四、await源码分析

```
public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            //将当前线程放入等待队列
            Node node = addConditionWaiter();
            //释放锁
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            //判断但概念线程是否从阻塞队列移除成功
            while (!isOnSyncQueue(node)) {
                //如果从阻塞队列移除成功，就把当前线程挂起
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            //该方法在上一篇介绍AQS已经介绍过，主要作用就是把当前节点的前一个节点状态改成signal
            //状态，改成这个状态之后就可以让前一个节点把自己唤醒
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                //将等待队列上处于CANCELLED（取消）状态的节点给干掉
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }
```

进入AbstractQueuedSynchronizer#ConditionObject#addConditionWaiter()

```
private Node addConditionWaiter() {
            //获取等待队列最后一个节点
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.

            //如果最后一个节点状态不是等待状态，说明有可能被取消了，处于取消状态
            //为什么会处于取消状态的呢？其实await还有其他类似方法的比如awaitNanos()可以传等待时间，如果超时就是取消状态
            if (t != null && t.waitStatus != Node.CONDITION) {
                //这个方法就是把队列中处于取消状态的节点给干掉，这个方法代码我觉得写的很怪，就不分析了。
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            //下面的就很简单了，就是把新建的节点挂到队列中
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }
```

进入AbstractQueuedSynchronizer#ConditionObject#fullyRelease()

```
final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            //获取AQS的state，注意这个值不一定是1，如果是重入锁的话，就会大于1
            //这里相当于把重入锁都释放掉
            int savedState = getState();
            //这个release就是上一篇文章中已经介绍的释放锁的过程
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed)
                //如果释放锁失败，就把当前线程改成CANCELLED状态
                node.waitStatus = Node.CANCELLED;
        }
    }
```

这个方法释放锁之后，处于阻塞队列中的第二个节点就会获取到锁（正常情况下），然后第一个节点就会被干掉。

## 五、await小结

　里面还有几个方法没有分析，那几个不是核心方法，就不分析了，这里await干的事情主要如下：

1. 将当前线程加入等待队列
2. 释放锁
3. 阻塞自己

## 六、signal/signalAll源码介绍

#### signal分析

```
public final void signal() {
            //判断当前线程是不是持有锁的线程，只有持有锁的线程才可以执行signal
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            //等待队列的首节点
            Node first = firstWaiter;
            //只有等待队列中有需要被唤醒的才会执行唤醒操作
            if (first != null)
                doSignal(first);
        }
```

进入#doSignal()方法

```
private void doSignal(Node first) {
    do {
        //将等待队列的首节点指向第二个节点
        if ( (firstWaiter = first.nextWaiter) == null)
            lastWaiter = null;
        first.nextWaiter = null;
     //不停的重试唤醒首节点
    } while (!transferForSignal(first) &&
             (first = firstWaiter) != null);
}
```

进入#transferForSignal()方法

```
final boolean transferForSignal(Node node) {
        //当当前节点的状态改成0，如果不成功一致重试
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;
        //将等待队列的首节点加入到阻塞队列的队尾，返回的p是阻塞队列倒数第二的节点
        Node p = enq(node);
        int ws = p.waitStatus;
        //如果在阻塞队列当前节点的前一个节点处于取消状态，或者把当前节点的前一个节点改成signal状态失败
        //就直接唤醒等待队列中的线程
        //这里这么做的原因是，如果在阻塞队列中，当前节点的前一个节点处于取消状态，那他就无法唤醒他之后的节点
        //如果改成signal不成功也是一样不能唤醒后面的节点
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }
```

好，到这里我们已经把处于await状态的线程给唤醒了，我们看一下唤醒之后处于等待的线程会干什么事情，我们再把await的代码看一下：

```
public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            //将当前线程放入等待队列
            Node node = addConditionWaiter();
            //释放锁
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            //判断但概念线程是否从阻塞队列移除成功
            while (!isOnSyncQueue(node)) {
                //如果从阻塞队列移除成功，就把当前线程挂起
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            //该方法在上一篇介绍AQS已经介绍过，主要作用就是把当前节点的前一个节点状态改成signal
            //状态，改成这个状态之后就可以让前一个节点把自己唤醒
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                //将等待队列上处于CANCELLED（取消）状态的节点给干掉
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }
```

当被唤醒之后，从第18行开始执行，acquireQueue就是去等待获取锁的方法，可以参考上一篇文章，至于后面几步不是核心步骤，就不分析了。

#### signalAll()分析

```
 public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }
```

进入#doSignalAll()方法

```
 private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }
```

从上面代码可以看出，signalAll和signal的区别就是signalAll会执行一个循环，把等待队列中的所有节点都执行一遍signal，就是说把所有等待队列中的节点全部加入到阻塞队列中，之后还是要一个节点一个节点的去慢慢获取锁。

## 七、Condition中await/signal和Object中的wait/notify对比

   ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200904153358725-301455340.png)

 

总结一下两者的不同点

-  使用ReetrantLock 和 Condition组合比使用synchronized 和 Object组合更灵活，因为Condition可以new多个，而Object却不行，比如在生产者消费者模型中，消费者可以唤醒生产者，生产者可以唤醒消费者，而使用Object的wait和notify却不行，notify要通知是一次性把生产者和消费者都通知了，因为他不能搞多个状态队列。
- awaitUninterruptibly()：这个方法可以让当前等待的线程不响应中断
- awaitUntil(Date deadline)：这玩意其实和await(long time,TimeUnit unit)一样，在等待时间之内可以被其它线程唤醒，等待时间一过该线程会自动唤醒，和别的线程争抢锁资源

## 八、总结

本片文章主要分析了下AQS中的Condition，介绍了Condition的等待和唤醒的原理，最后把Condition和Ojbect的等待唤醒机制做了一个对比，希望大家可以从本文中了解Condition的原理。

## 九、参考

[AQS-Condition介绍](https://www.cnblogs.com/gunduzi/p/13614429.html)



## END