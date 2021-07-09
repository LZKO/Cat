# AQS-共享模式分析

## 一、概述

一般我们在使用锁的时候，是要求两个线程之间严格互斥的，即一次只能一个线程获取到锁，但是在有些场景下是可以一次有多个线程获取到锁，当然这个时候不叫锁，换了一种说法，叫做资源。比如生产者消费者模型，实际上我们是可以让多个生产者和消费者同时工作的，如果使用互斥锁，那一次只能让一个线程去生产或者去消费，效率太低，Java中有一个类就是实现这种可以同时让多个线程获取到资源，就是Semaphore,而Semaphore的基础就是基于AQS的共享模式实现的，本文就从Semaphore获取资源，释放资源着手，来分析一下AQS共享模式处理方式。

　　在上一篇文章中已经介绍AQS的独占模式，里面详细介绍了AQS实现原理，如果上文已经介绍过的内容，本文不会再重复分析。

## 二、Semaphore示例

先贴出来一个简单的Sempaphore使用例子，从这个例子出发，分析AQS的共享模式

```
public void semaporeTest(){
        Semaphore semaphore = new Semaphore(1);
        new Thread(()->{
            try {
                semaphore.acquire();
                System.out.println(Thread.currentThread().getName());
                Thread.sleep(5000);
                System.out.println(semaphore.getQueueLength());
                semaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(()->{
            try {
                semaphore.acquire();
                System.out.println(Thread.currentThread().getName());
                Thread.sleep(2000);
                semaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
```

例子中，资源个数设置成了1，这个其实就是互斥锁，在操作系统中信号量如果资源个数为1，就是二元信号量，可以实现互斥的功能，下面我们就分析一下获取资源和释放资源的方法。

## 三、AQS共享模式获取资源

进入Semaphore#acquire()方法

```
    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
```

进入AbstractQueuedSynchronizer#acquireSharedInterruptibly()方法

```
public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {        //如果发生中断直接抛出异常，就是当前方法不能中断
        if (Thread.interrupted())
            throw new InterruptedException();        //尝试获取共享模式资源
        if (tryAcquireShared(arg) < 0)            //如果获取资源失败处理方法
            doAcquireSharedInterruptibly(arg);
    }
```

进入AbstractQueuedSynchronizer#tryAcquireShared()

```
protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }
```

这个方法和tryAcquire()一样，在AbstractQueuedSynchronizer中都没有实现，需要子类自己实现，我们看一下Semaphore非公平模式下的实现方法

```
protected int tryAcquireShared(int acquires) {
            return nonfairTryAcquireShared(acquires);
        }
 final int nonfairTryAcquireShared(int acquires) {
            for (;;) {
                //获取AQS中资源个数
                int available = getState();
                int remaining = available - acquires;
                //如果remaining小于0，说明没有可用的资源了，如果大于0，执行CAS操作获取资源，最后返回剩余的资源数
                //如果返回的剩余资源数小于或者等于0，说明没有可用资源了，如果大于0，说明还有可用资源
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }
```

ok,我们再回到AbstractQueuedSynchronizer#acquireSharedInterruptibly()方法

```
public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        //如果发生中断直接抛出异常，就是当前方法不能中断
        if (Thread.interrupted())
            throw new InterruptedException();
        //尝试获取共享模式资源
        if (tryAcquireShared(arg) < 0)
            //如果获取资源失败处理方法
            doAcquireSharedInterruptibly(arg);
    }
```

如果tryAcquireShared(arg) < 0,说明剩余资源不够，获取失败，下面的方法就是当前没有可用资源，线程需要等待，我们进入AbstractQueuedSynchronizer#doAcquireSharedInterruptibly()方法

```
private void doAcquireSharedInterruptibly(int arg)
        throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```

大家可以对比一下[AQS-独占模式分析](https://www.cnblogs.com/gunduzi/p/13608203.html)，文章中的acquireQueued()方法，只有两点不同第一个就是setHeadAndPropagate方法，第二个该方法在阻塞过程中不可中断，这里就只分析一下setHeadAndPropagate方法，剩下的方法在上一篇文章中都分析过了，就不重复分析了。

进入AbstractQueuedSynchronizer#setHeadAndPropagate()

```
private void setHeadAndPropagate(Node node, int propagate) {        //这一块和独占模式的处理方式一样
        Node h = head; // Record old head for check below
        setHead(node);
        /*
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */          //propagate表示剩余资源的数量，这个判断看着很令人费解        //下面我会着重分析这个判断
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }
```

这个方法中的判断写了那么一大串，看着很令人费解，那就一个条件一个条件分析。

1. propagate > 0，说明线程获取锁成功，且有剩余资源可以获取，所以就继续唤醒队列中的线程把剩余的资源给占用了
2. h == null和 (h = head) == null，这两个条件其实不会成立，因为只要往队列中添加了元素，队列中就至少会有一个节点，写这两个条件是为了防止空指针异常的
3. h.waitStatus < 0这个条件是为了检测首节点后面还有没有节点，在shouldParkAfterFailedAcquire方法中，每个入队的节点都会把他前面一个节点的状态改成signal = -1状态，目的是为了让前面一个节点把自己唤醒，其实就是在这里体现的，这个条件如果成立，就说明首节点后面面还有未被唤醒的节点。
4. 最后一个h.waitStatus < 0,是不是很费解，既然，前面已经判断过一次首节点状态是不是小于0，这里为什么还要再判断一次，这里的h是第四个条件(h = head) = null，重新获取的，也就是说前面一个h.waitStatus < 0不成立，重新获取一下首节点，再判断一次就有可能会成立，为什么再判断一次就可能成立呢？这个就和doReleaseShared()方法有关了，我们先分析这个方法，再回头看这个。

进入AbstractQueuedSynchronizer#doReleaseShared()方法

```
private void doReleaseShared() {
        for (;;) {
            //获取首节点
            Node h = head;
            if (h != null && h != tail) {
                //获取首节点状态
                int ws = h.waitStatus;
                //如果首节点状态是SIGNAL，说明首节点后面还有节点，唤醒他们
                if (ws == Node.SIGNAL) {
                    //先把首节点状态改成0，0可以看成首节点的中间状态，只有在唤醒第二个节点的时候才会存在，当第二个节点唤醒之后，首节点
                    //就会被干掉
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases

                    //这个方法就是唤醒首节点之后第一个处于非取消状态的节点
                    unparkSuccessor(h);
                }
                //这里就有意思了
                //这里判断ws == 0，这个是中间状态，就是说有一个线程正在唤醒第二个节点，这个时候，又有一个线程释放了资源，也要来唤醒第二个节点，但是他发现
                //有别的线程在处理，他就把这个状态改成PROPAGATE = -3,而这个状态正是上一个方法需要判断的，上一个方法判断h.waitStatus < 0，会成立就是这里设置的                //当然，h.waitStatus < 0会成立，还有别的原因，这个只是其中一个，下面会分析
                else if (ws == 0 &&
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            if (h == head)                   // loop if head changed
                break;
        }
    }
```

这个方法分析完了，我们回到上面没有分析完的地方继续分析，在上面的分析中说，第一个h.waitStauts < 0不成立，而第二次判断h.waitStatus < 0就可能会成立，原因可能有两个，如下：

- 第一个原因就是doReleaseShared方法中的如下代码导致的：

```
else if (ws == 0 &&
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
```

这段代码会把首节点处于0状态改成-3，如果setHeadAndPropagate方法在执行第一个h.waitStatus < 0的时候，状态刚刚好为0，那这个条件就不成立，但是如果这个时候有另一个线程把首节点状态改成-3，那第二个h.waitStatus < 0就会成立。

- 第二个原因就是，当h.waitStatus = 0的时候，说明有一个线程释放了资源，而且正在唤醒第二个节点，所以判断第一个h.waitStatus < 0条件不成立，当第二个节点获取到锁之后，把第一个节点干掉，那新的首节点状态为SIGNAL = -1，所以第二个判断h.waitStatus < 0成立。

分析到这里，上面的if判断分析完了，但是有一个问题？作者为什么这么写，在setHeadAndPropagate方法中有这么一段注释：

```java
 /*
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */
```

这段话的意思是说，这个if判断有可能会导致不必要的唤醒,但是只有当大量的获取锁和释放锁的时候才会发生，而且唤醒之后如果获取不到锁，还会继续阻塞，作者这么做应该是为了防止出现一些难以预料的bug，所以就容忍了这个没有必要的唤醒。

上面提到了没有必要的唤醒，那什么时候会造成没有必要的唤醒呢？考虑下面的情况：

1. propagate = 0,但是第一个h.waitStatus < 0，这种情况是正常情况，因为首节点正常就处在SIGNAL = -1状态
2. propagate = 0,第一个h.waitStatus < 0不成立，第二个h.waitStatus < 0成立，这个就对应上面分析两种原因
3. propagate > 0，比如propagate = 1,在这个if判断还没有执行的时候，另一个线程进来把这个资源给占用，这个时候其实已经没有资源了，当然，这种情况发生的可能性比较小

OK，这个方法的分析到此为止，脑壳痛。。。

　　　　　　　　![img](C:\lyy\project_workspace\Cat\src\main\notes\interview\pic\1407685-20200904210409726-899718318.png)

## 四、AQS共享模式释放资源

进入Semaphore#release()

```
public void release() {
        sync.releaseShared(1);
    }
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
        doReleaseShared();
        return true;
    }
    return false;
}
```

实际上这里调用的就是上面已经分析过的doReleaseShared()方法。

## 五、AQS共享模式和独占模式异同

共享模式和独占模式大致上来说差别不大，独占模式如果把资源数量设置成1，那就和共享模式功能一样，从代码实现上来看，共享模式和独占模式最大的不同就是共享模式在一个线程获取到资源之后，发现还有剩余资源，还会唤醒线程，而独占模式获取锁之后没有这个过程。

　　无论是共享模式还是独占模式都是在资源不足的时候，把线程放入阻塞队列中，当有线程释放资源的时候唤醒队列中的线程。

## 六、参考

[AQS-共享模式分析](https://www.cnblogs.com/gunduzi/p/13615810.html)





