#### AQS源码导读

参考：<https://javadoop.com/post/AbstractQueuedSynchronizer>

在分析 Java 并发包 java.util.concurrent 源码的时候，少不了需要了解 AbstractQueuedSynchronizer（以下简写AQS）这个抽象类，因为它是 Java 并发包的基础工具类，是实现 ReentrantLock、CountDownLatch、Semaphore、FutureTask 等类的基础。

Google 一下 AbstractQueuedSynchronizer，我们可以找到很多关于 AQS 的介绍，但是很多都没有介绍清楚，因为大部分文章没有把其中的一些关键的细节说清楚。

本文将从 ReentrantLock 的公平锁源码出发，分析下 AbstractQueuedSynchronizer 这个类是怎么工作的，希望能给大家提供一些简单的帮助。

申明以下几点：

- 本文有点长，但还是挺简单，主要面向读者对象为并发编程的初学者，或者想要阅读 Java 并发包源码的开发者。对于新手来说，可能需要花好几个小时才能完全看懂，但是这时间肯定是值得的。
- 源码环境 JDK1.7（1.8没啥变化），看到不懂或有疑惑的部分，最好能自己打开源码看看。Doug Lea 大神的代码写得真心不错。
- 本文不分析共享模式，这样可以给读者减少很多负担，[第三篇文章](https://javadoop.com/post/AbstractQueuedSynchronizer-3)对共享模式进行了分析。而且也不分析 condition 部分，所以应该说很容易就可以看懂了。
- 本文大量使用我们平时用得最多的 ReentrantLock 的概念，本质上来说是不正确的，读者应该清楚，AQS 不仅仅用来实现可重入锁，只是希望读者可以用锁来联想 AQS 的使用场景，降低阅读压力。
- ReentrantLock 的公平锁和非公平锁只有一点点区别，[第二篇文章](https://javadoop.com/post/AbstractQueuedSynchronizer-2)做了介绍。
- 评论区有读者反馈本文直接用代码说不友好，应该多配点流程图，这篇文章确实有这个问题。但是作为过来人，我想告诉大家，对于 AQS 来说，形式真的不重要，重要的是把细节说清楚。

##### AQS 结构

先来看看 AQS 有哪些属性，搞清楚这些基本就知道 AQS 是什么套路了，毕竟可以猜嘛！

```java
// 头结点，你直接把它当做 当前持有锁的线程 可能是最好理解的
private transient volatile Node head;

// 阻塞的尾节点，每个新的节点进来，都插入到最后，也就形成了一个链表
private transient volatile Node tail;

// 这个是最重要的，代表当前锁的状态，0代表没有被占用，大于 0 代表有线程持有当前锁
// 这个值可以大于 1，是因为锁可以重入，每次重入都加上 1
private volatile int state;

// 代表当前持有独占锁的线程，举个最重要的使用例子，因为锁可以重入
// reentrantLock.lock()可以嵌套调用多次，所以每次用这个来判断当前线程是否已经拥有了锁
// if (currentThread == getExclusiveOwnerThread()) {state++}
private transient Thread exclusiveOwnerThread; //继承自AbstractOwnableSynchronizer
```

怎么样，看样子应该是很简单的吧，毕竟也就四个属性啊。

AbstractQueuedSynchronizer 的等待队列示意如下所示，注意了，之后分析过程中所说的 queue，也就是阻塞队列**不包含 head，不包含 head，不包含 head**。

![aqs-0](https://www.javadoop.com/blogimages/AbstractQueuedSynchronizer/aqs-0.png)

![1595149489187](https://github.com/LZKO/Cat/blob/master/src/main/notes/interview/assets/1595149489187.png)

（PS：上图为测试本地图片上传）

等待队列中每个线程被包装成一个 Node 实例，数据结构是链表，一起看看源码吧：

```java
static final class Node {
    // 标识节点当前在共享模式下
    static final Node SHARED = new Node();
    // 标识节点当前在独占模式下
    static final Node EXCLUSIVE = null;

    // ======== 下面的几个int常量是给waitStatus用的 ===========
    /** waitStatus value to indicate thread has cancelled */
    // 代码此线程取消了争抢这个锁
    static final int CANCELLED =  1;
    /** waitStatus value to indicate successor's thread needs unparking */
    // 官方的描述是，其表示当前node的后继节点对应的线程需要被唤醒
    static final int SIGNAL    = -1;
    /** waitStatus value to indicate thread is waiting on condition */
    // 本文不分析condition，所以略过吧，下一篇文章会介绍这个
    static final int CONDITION = -2;
    /**
     * waitStatus value to indicate the next acquireShared should
     * unconditionally propagate
     */
    // 同样的不分析，略过吧
    static final int PROPAGATE = -3;
    // =====================================================


    // 取值为上面的1、-1、-2、-3，或者0(以后会讲到)
    // 这么理解，暂时只需要知道如果这个值 大于0 代表此线程取消了等待，
    //    ps: 半天抢不到锁，不抢了，ReentrantLock是可以指定timeouot的。。。
    volatile int waitStatus;
    // 前驱节点的引用
    volatile Node prev;
    // 后继节点的引用
    volatile Node next;
    // 这个就是线程本尊
    volatile Thread thread;

}
```

Node 的数据结构其实也挺简单的，就是 thread + waitStatus + pre + next 四个属性而已，大家先要有这个概念在心里。

上面的是基础知识，后面会多次用到，心里要时刻记着它们，心里想着这个结构图就可以了。下面，我们开始说 ReentrantLock 的公平锁。再次强调，我说的阻塞队列不包含 head 节点。

![aqs-0](https://www.javadoop.com/blogimages/AbstractQueuedSynchronizer/aqs-0.png)

首先，我们先看下 ReentrantLock 的使用方式。

```java
// 我用个web开发中的service概念吧
public class OrderService {
    // 使用static，这样每个线程拿到的是同一把锁，当然，spring mvc中service默认就是单例，别纠结这个
    private static ReentrantLock reentrantLock = new ReentrantLock(true);

    public void createOrder() {
        // 比如我们同一时间，只允许一个线程创建订单
        reentrantLock.lock();
        // 通常，lock 之后紧跟着 try 语句
        try {
            // 这块代码同一时间只能有一个线程进来(获取到锁的线程)，
            // 其他的线程在lock()方法上阻塞，等待获取到锁，再进来
            // 执行代码...
            // 执行代码...
            // 执行代码...
        } finally {
            // 释放锁
            reentrantLock.unlock();
        }
    }
}
```

ReentrantLock 在内部用了内部类 Sync 来管理锁，所以真正的获取锁和释放锁是由 Sync 的实现类来控制的。

```java
abstract static class Sync extends AbstractQueuedSynchronizer {
}
```

Sync 有两个实现，分别为 NonfairSync（非公平锁）和 FairSync（公平锁），我们看 FairSync 部分。

```java
public ReentrantLock(boolean fair) {
    sync = fair ? new FairSync() : new NonfairSync();
}
```

##### 线程抢锁

很多人肯定开始嫌弃上面废话太多了，下面跟着代码走，我就不废话了。

```java
static final class FairSync extends Sync {
    private static final long serialVersionUID = -3000897897090466540L;
      // 争锁
    final void lock() {
        acquire(1);
    }
      // 来自父类AQS，我直接贴过来这边，下面分析的时候同样会这样做，不会给读者带来阅读压力
    // 我们看到，这个方法，如果tryAcquire(arg) 返回true, 也就结束了。
    // 否则，acquireQueued方法会将线程压到队列中
    public final void acquire(int arg) { // 此时 arg == 1
        // 首先调用tryAcquire(1)一下，名字上就知道，这个只是试一试
        // 因为有可能直接就成功了呢，也就不需要进队列排队了，
        // 对于公平锁的语义就是：本来就没人持有锁，根本没必要进队列等待(又是挂起，又是等待被唤醒的)
        if (!tryAcquire(arg) &&
            // tryAcquire(arg)没有成功，这个时候需要把当前线程挂起，放到阻塞队列中。
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
              selfInterrupt();
        }
    }

    /**
     * Fair version of tryAcquire.  Don't grant access unless
     * recursive call or no waiters or is first.
     */
    // 尝试直接获取锁，返回值是boolean，代表是否获取到锁
    // 返回true：1.没有线程在等待锁；2.重入锁，线程本来就持有锁，也就可以理所当然可以直接获取
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        // state == 0 此时此刻没有线程持有锁
        if (c == 0) {
            // 虽然此时此刻锁是可以用的，但是这是公平锁，既然是公平，就得讲究先来后到，
            // 看看有没有别人在队列中等了半天了
            if (!hasQueuedPredecessors() &&
                // 如果没有线程在等待，那就用CAS尝试一下，成功了就获取到锁了，
                // 不成功的话，只能说明一个问题，就在刚刚几乎同一时刻有个线程抢先了 =_=
                // 因为刚刚还没人的，我判断过了
                compareAndSetState(0, acquires)) {

                // 到这里就是获取到锁了，标记一下，告诉大家，现在是我占用了锁
                setExclusiveOwnerThread(current);
                return true;
            }
        }
          // 会进入这个else if分支，说明是重入了，需要操作：state=state+1
        // 这里不存在并发问题
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;
            if (nextc < 0)
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        // 如果到这里，说明前面的if和else if都没有返回true，说明没有获取到锁
        // 回到上面一个外层调用方法继续看:
        // if (!tryAcquire(arg) 
        //        && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) 
        //     selfInterrupt();
        return false;
    }

    // 假设tryAcquire(arg) 返回false，那么代码将执行：
      //        acquireQueued(addWaiter(Node.EXCLUSIVE), arg)，
    // 这个方法，首先需要执行：addWaiter(Node.EXCLUSIVE)

    /**
     * Creates and enqueues node for current thread and given mode.
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node
     */
    // 此方法的作用是把线程包装成node，同时进入到队列中
    // 参数mode此时是Node.EXCLUSIVE，代表独占模式
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        // 以下几行代码想把当前node加到链表的最后面去，也就是进到阻塞队列的最后
        Node pred = tail;

        // tail!=null => 队列不为空(tail==head的时候，其实队列是空的，不过不管这个吧)
        if (pred != null) { 
            // 将当前的队尾节点，设置为自己的前驱 
            node.prev = pred; 
            // 用CAS把自己设置为队尾, 如果成功后，tail == node 了，这个节点成为阻塞队列新的尾巴
            if (compareAndSetTail(pred, node)) { 
                // 进到这里说明设置成功，当前node==tail, 将自己与之前的队尾相连，
                // 上面已经有 node.prev = pred，加上下面这句，也就实现了和之前的尾节点双向连接了
                pred.next = node;
                // 线程入队了，可以返回了
                return node;
            }
        }
        // 仔细看看上面的代码，如果会到这里，
        // 说明 pred==null(队列是空的) 或者 CAS失败(有线程在竞争入队)
        // 读者一定要跟上思路，如果没有跟上，建议先不要往下读了，往回仔细看，否则会浪费时间的
        enq(node);
        return node;
    }

    /**
     * Inserts node into queue, initializing if necessary. See picture above.
     * @param node the node to insert
     * @return node's predecessor
     */
    // 采用自旋的方式入队
    // 之前说过，到这个方法只有两种可能：等待队列为空，或者有线程竞争入队，
    // 自旋在这边的语义是：CAS设置tail过程中，竞争一次竞争不到，我就多次竞争，总会排到的
    private Node enq(final Node node) {
        for (;;) {
            Node t = tail;
            // 之前说过，队列为空也会进来这里
            if (t == null) { // Must initialize
                // 初始化head节点
                // 细心的读者会知道原来 head 和 tail 初始化的时候都是 null 的
                // 还是一步CAS，你懂的，现在可能是很多线程同时进来呢
                if (compareAndSetHead(new Node()))
                    // 给后面用：这个时候head节点的waitStatus==0, 看new Node()构造方法就知道了

                    // 这个时候有了head，但是tail还是null，设置一下，
                    // 把tail指向head，放心，马上就有线程要来了，到时候tail就要被抢了
                    // 注意：这里只是设置了tail=head，这里可没return哦，没有return，没有return
                    // 所以，设置完了以后，继续for循环，下次就到下面的else分支了
                    tail = head;
            } else {
                // 下面几行，和上一个方法 addWaiter 是一样的，
                // 只是这个套在无限循环里，反正就是将当前线程排到队尾，有线程竞争的话排不上重复排
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }


    // 现在，又回到这段代码了
    // if (!tryAcquire(arg) 
    //        && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) 
    //     selfInterrupt();

    // 下面这个方法，参数node，经过addWaiter(Node.EXCLUSIVE)，此时已经进入阻塞队列
    // 注意一下：如果acquireQueued(addWaiter(Node.EXCLUSIVE), arg))返回true的话，
    // 意味着上面这段代码将进入selfInterrupt()，所以正常情况下，下面应该返回false
    // 这个方法非常重要，应该说真正的线程挂起，然后被唤醒后去获取锁，都在这个方法里了
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                final Node p = node.predecessor();
                // p == head 说明当前节点虽然进到了阻塞队列，但是是阻塞队列的第一个，因为它的前驱是head
                // 注意，阻塞队列不包含head节点，head一般指的是占有锁的线程，head后面的才称为阻塞队列
                // 所以当前节点可以去试抢一下锁
                // 这里我们说一下，为什么可以去试试：
                // 首先，它是队头，这个是第一个条件，其次，当前的head有可能是刚刚初始化的node，
                // enq(node) 方法里面有提到，head是延时初始化的，而且new Node()的时候没有设置任何线程
                // 也就是说，当前的head不属于任何一个线程，所以作为队头，可以去试一试，
                // tryAcquire已经分析过了, 忘记了请往前看一下，就是简单用CAS试操作一下state
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                // 到这里，说明上面的if分支没有成功，要么当前node本来就不是队头，
                // 要么就是tryAcquire(arg)没有抢赢别人，继续往下看
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            // 什么时候 failed 会为 true???
            // tryAcquire() 方法抛异常的情况
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Checks and updates status for a node that failed to acquire.
     * Returns true if thread should block. This is the main signal
     * control in all acquire loops.  Requires that pred == node.prev
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     */
    // 刚刚说过，会到这里就是没有抢到锁呗，这个方法说的是："当前线程没有抢到锁，是否需要挂起当前线程？"
    // 第一个参数是前驱节点，第二个参数才是代表当前线程的节点
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        // 前驱节点的 waitStatus == -1 ，说明前驱节点状态正常，当前线程需要挂起，直接可以返回true
        if (ws == Node.SIGNAL)
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;

        // 前驱节点 waitStatus大于0 ，之前说过，大于0 说明前驱节点取消了排队。
        // 这里需要知道这点：进入阻塞队列排队的线程会被挂起，而唤醒的操作是由前驱节点完成的。
        // 所以下面这块代码说的是将当前节点的prev指向waitStatus<=0的节点，
        // 简单说，就是为了找个好爹，因为你还得依赖它来唤醒呢，如果前驱节点取消了排队，
        // 找前驱节点的前驱节点做爹，往前遍历总能找到一个好爹的
        if (ws > 0) {
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             */
            // 仔细想想，如果进入到这个分支意味着什么
            // 前驱节点的waitStatus不等于-1和1，那也就是只可能是0，-2，-3
            // 在我们前面的源码中，都没有看到有设置waitStatus的，所以每个新的node入队时，waitStatu都是0
            // 正常情况下，前驱节点是之前的 tail，那么它的 waitStatus 应该是 0
            // 用CAS将前驱节点的waitStatus设置为Node.SIGNAL(也就是-1)
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        // 这个方法返回 false，那么会再走一次 for 循序，
        //     然后再次进来此方法，此时会从第一个分支返回 true
        return false;
    }

    // private static boolean shouldParkAfterFailedAcquire(Node pred, Node node)
    // 这个方法结束根据返回值我们简单分析下：
    // 如果返回true, 说明前驱节点的waitStatus==-1，是正常情况，那么当前线程需要被挂起，等待以后被唤醒
    //        我们也说过，以后是被前驱节点唤醒，就等着前驱节点拿到锁，然后释放锁的时候叫你好了
    // 如果返回false, 说明当前不需要被挂起，为什么呢？往后看

    // 跳回到前面是这个方法
    // if (shouldParkAfterFailedAcquire(p, node) &&
    //                parkAndCheckInterrupt())
    //                interrupted = true;

    // 1. 如果shouldParkAfterFailedAcquire(p, node)返回true，
    // 那么需要执行parkAndCheckInterrupt():

    // 这个方法很简单，因为前面返回true，所以需要挂起线程，这个方法就是负责挂起线程的
    // 这里用了LockSupport.park(this)来挂起线程，然后就停在这里了，等待被唤醒=======
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    // 2. 接下来说说如果shouldParkAfterFailedAcquire(p, node)返回false的情况

   // 仔细看shouldParkAfterFailedAcquire(p, node)，我们可以发现，其实第一次进来的时候，一般都不会返回true的，原因很简单，前驱节点的waitStatus=-1是依赖于后继节点设置的。也就是说，我都还没给前驱设置-1呢，怎么可能是true呢，但是要看到，这个方法是套在循环里的，所以第二次进来的时候状态就是-1了。

    // 解释下为什么shouldParkAfterFailedAcquire(p, node)返回false的时候不直接挂起线程：
    // => 是为了应对在经过这个方法后，node已经是head的直接后继节点了。剩下的读者自己想想吧。
}
```

说到这里，也就明白了，多看几遍 `final boolean acquireQueued(final Node node, int arg)` 这个方法吧。自己推演下各个分支怎么走，哪种情况下会发生什么，走到哪里。

##### 解锁操作

最后，就是还需要介绍下唤醒的动作了。我们知道，正常情况下，如果线程没获取到锁，线程会被 `LockSupport.park(this);` 挂起停止，等待被唤醒。

```java
// 唤醒的代码还是比较简单的，你如果上面加锁的都看懂了，下面都不需要看就知道怎么回事了
public void unlock() {
    sync.release(1);
}

public final boolean release(int arg) {
    // 往后看吧
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);
        return true;
    }
    return false;
}

// 回到ReentrantLock看tryRelease方法
protected final boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    // 是否完全释放锁
    boolean free = false;
    // 其实就是重入的问题，如果c==0，也就是说没有嵌套锁了，可以释放了，否则还不能释放掉
    if (c == 0) {
        free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c);
    return free;
}

/**
 * Wakes up node's successor, if one exists.
 *
 * @param node the node
 */
// 唤醒后继节点
// 从上面调用处知道，参数node是head头结点
private void unparkSuccessor(Node node) {
    /*
     * If status is negative (i.e., possibly needing signal) try
     * to clear in anticipation of signalling.  It is OK if this
     * fails or if status is changed by waiting thread.
     */
    int ws = node.waitStatus;
    // 如果head节点当前waitStatus<0, 将其修改为0
    if (ws < 0)
        compareAndSetWaitStatus(node, ws, 0);
    /*
     * Thread to unpark is held in successor, which is normally
     * just the next node.  But if cancelled or apparently null,
     * traverse backwards from tail to find the actual
     * non-cancelled successor.
     */
    // 下面的代码就是唤醒后继节点，但是有可能后继节点取消了等待（waitStatus==1）
    // 从队尾往前找，找到waitStatus<=0的所有节点中排在最前面的
    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        // 从后往前找，仔细看代码，不必担心中间有节点取消(waitStatus==1)的情况
        for (Node t = tail; t != null && t != node; t = t.prev)
            if (t.waitStatus <= 0)
                s = t;
    }
    if (s != null)
        // 唤醒线程
        LockSupport.unpark(s.thread);
}
```

唤醒线程以后，被唤醒的线程将从以下代码中继续往前走：

```java
private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this); // 刚刚线程被挂起在这里了
    return Thread.interrupted();
}
// 又回到这个方法了：acquireQueued(final Node node, int arg)，这个时候，node的前驱是head了
```

好了，后面就不分析源码了，剩下的还有问题自己去仔细看看代码吧。

##### 总结

总结一下吧。

在并发环境下，加锁和解锁需要以下三个部件的协调：

1. 锁状态。我们要知道锁是不是被别的线程占有了，这个就是 state 的作用，它为 0 的时候代表没有线程占有锁，可以去争抢这个锁，用 CAS 将 state 设为 1，如果 CAS 成功，说明抢到了锁，这样其他线程就抢不到了，如果锁重入的话，state进行 +1 就可以，解锁就是减 1，直到 state 又变为 0，代表释放锁，所以 lock() 和 unlock() 必须要配对啊。然后唤醒等待队列中的第一个线程，让其来占有锁。
2. 线程的阻塞和解除阻塞。AQS 中采用了 LockSupport.park(thread) 来挂起线程，用 unpark 来唤醒线程。
3. 阻塞队列。因为争抢锁的线程可能很多，但是只能有一个线程拿到锁，其他的线程都必须等待，这个时候就需要一个 queue 来管理这些线程，AQS 用的是一个 FIFO 的队列，就是一个链表，每个 node 都持有后继节点的引用。AQS 采用了 CLH 锁的变体来实现，感兴趣的读者可以参考这篇文章[关于CLH的介绍](http://coderbee.net/index.php/concurrent/20131115/577)，写得简单明了。

##### 示例图解析

下面属于回顾环节，用简单的示例来说一遍，如果上面的有些东西没看懂，这里还有一次帮助你理解的机会。

首先，第一个线程调用 reentrantLock.lock()，翻到最前面可以发现，tryAcquire(1) 直接就返回 true 了，结束。只是设置了 state=1，连 head 都没有初始化，更谈不上什么阻塞队列了。要是线程 1 调用 unlock() 了，才有线程 2 来，那世界就太太太平了，完全没有交集嘛，那我还要 AQS 干嘛。

如果线程 1 没有调用 unlock() 之前，线程 2 调用了 lock(), 想想会发生什么？

线程 2 会初始化 head【new Node()】，同时线程 2 也会插入到阻塞队列并挂起 (注意看这里是一个 for 循环，而且设置 head 和 tail 的部分是不 return 的，只有入队成功才会跳出循环)

```java
private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        if (t == null) { // Must initialize
            if (compareAndSetHead(new Node()))
                tail = head;
        } else {
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}
```

首先，是线程 2 初始化 head 节点，此时 head==tail, waitStatus==0

![aqs-1](https://www.javadoop.com/blogimages/AbstractQueuedSynchronizer/aqs-1.png)

然后线程 2 入队：

![aqs-2](https://www.javadoop.com/blogimages/AbstractQueuedSynchronizer/aqs-2.png)

同时我们也要看此时节点的 waitStatus，我们知道 head 节点是线程 2 初始化的，此时的 waitStatus 没有设置， java 默认会设置为 0，但是到 shouldParkAfterFailedAcquire 这个方法的时候，线程 2 会把前驱节点，也就是 head 的waitStatus设置为 -1。

那线程 2 节点此时的 waitStatus 是多少呢，由于没有设置，所以是 0；

如果线程 3 此时再进来，直接插到线程 2 的后面就可以了，此时线程 3 的 waitStatus 是 0，到 shouldParkAfterFailedAcquire 方法的时候把前驱节点线程 2 的 waitStatus 设置为 -1。

![aqs-3](https://www.javadoop.com/blogimages/AbstractQueuedSynchronizer/aqs-3.png)

这里可以简单说下 waitStatus 中 SIGNAL(-1) 状态的意思，Doug Lea 注释的是：代表后继节点需要被唤醒。也就是说这个 waitStatus 其实代表的不是自己的状态，而是后继节点的状态，我们知道，每个 node 在入队的时候，都会把前驱节点的状态改为 SIGNAL，然后阻塞，等待被前驱唤醒。这里涉及的是两个问题：有线程取消了排队、唤醒操作。其实本质是一样的，读者也可以顺着 “waitStatus代表后继节点的状态” 这种思路去看一遍源码。

（全文完）



参考：<https://javadoop.com/post/AbstractQueuedSynchronizer-2>

文章比较长，信息量比较大，建议在 pc 上阅读。文章标题是为了呼应前文，其实可以单独成文的，主要是希望读者看文章能系统看。

本文关注以下几点内容：

1. 深入理解 ReentrantLock 公平锁和非公平锁的区别
2. 深入分析 AbstractQueuedSynchronizer 中的 ConditionObject
3. 深入理解 Java 线程中断和 InterruptedException 异常

基本上本文把以上几点都说清楚了，我假设读者看过[上一篇文章中对 AbstractQueuedSynchronizer 的介绍 ](http://hongjiev.github.io/2017/06/16/AbstractQueuedSynchronizer/)，当然如果你已经熟悉 AQS 中的独占锁了，那也可以直接看这篇。各小节之间基本上没什么关系，大家可以只关注自己感兴趣的部分。

其实这篇文章的信息量很大，初学者估计**至少要 1 小时**才能看完，希望本文对得起大家的时间。

##### 公平锁和非公平锁

ReentrantLock 默认采用非公平锁，除非你在构造方法中传入参数 true 。

```java
public ReentrantLock() {
    // 默认非公平锁
    sync = new NonfairSync();
}
public ReentrantLock(boolean fair) {
    sync = fair ? new FairSync() : new NonfairSync();
}
```

公平锁的 lock 方法：

```java
static final class FairSync extends Sync {
    final void lock() {
        acquire(1);
    }
    // AbstractQueuedSynchronizer.acquire(int arg)
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            // 1. 和非公平锁相比，这里多了一个判断：是否有线程在等待
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

非公平锁的 lock 方法：

```java
static final class NonfairSync extends Sync {
    final void lock() {
        // 2. 和公平锁相比，这里会直接先进行一次CAS，成功就返回了
        if (compareAndSetState(0, 1))
            setExclusiveOwnerThread(Thread.currentThread());
        else
            acquire(1);
    }
    // AbstractQueuedSynchronizer.acquire(int arg)
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
    protected final boolean tryAcquire(int acquires) {
        return nonfairTryAcquire(acquires);
    }
}
/**
 * Performs non-fair tryLock.  tryAcquire is implemented in
 * subclasses, but both need nonfair try for trylock method.
 */
final boolean nonfairTryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        // 这里没有对阻塞队列进行判断
        if (compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0) // overflow
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}
```

总结：公平锁和非公平锁只有两处不同：

1. 非公平锁在调用 lock 后，首先就会调用 CAS 进行一次抢锁，如果这个时候恰巧锁没有被占用，那么直接就获取到锁返回了。
2. 非公平锁在 CAS 失败后，和公平锁一样都会进入到 tryAcquire 方法，在 tryAcquire 方法中，如果发现锁这个时候被释放了（state == 0），非公平锁会直接 CAS 抢锁，但是公平锁会判断等待队列是否有线程处于等待状态，如果有则不去抢锁，乖乖排到后面。

公平锁和非公平锁就这两点区别，如果这两次 CAS 都不成功，那么后面非公平锁和公平锁是一样的，都要进入到阻塞队列等待唤醒。

相对来说，非公平锁会有更好的性能，因为它的吞吐量比较大。当然，非公平锁让获取锁的时间变得更加不确定，可能会导致在阻塞队列中的线程长期处于饥饿状态。

##### Condition

Tips: 这里重申一下，要看懂这个，必须要先看懂上一篇关于 [AbstractQueuedSynchronizer](http://hongjiev.github.io/2017/06/16/AbstractQueuedSynchronizer/) 的介绍，或者你已经有相关的知识了，否则这节肯定是看不懂的。

我们先来看看 Condition 的使用场景，Condition 经常可以用在**生产者-消费者**的场景中，请看 Doug Lea 给出的这个例子：

```java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class BoundedBuffer {
    final Lock lock = new ReentrantLock();
    // condition 依赖于 lock 来产生
    final Condition notFull = lock.newCondition();
    final Condition notEmpty = lock.newCondition();

    final Object[] items = new Object[100];
    int putptr, takeptr, count;

    // 生产
    public void put(Object x) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length)
                notFull.await();  // 队列已满，等待，直到 not full 才能继续生产
            items[putptr] = x;
            if (++putptr == items.length) putptr = 0;
            ++count;
            notEmpty.signal(); // 生产成功，队列已经 not empty 了，发个通知出去
        } finally {
            lock.unlock();
        }
    }

    // 消费
    public Object take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0)
                notEmpty.await(); // 队列为空，等待，直到队列 not empty，才能继续消费
            Object x = items[takeptr];
            if (++takeptr == items.length) takeptr = 0;
            --count;
            notFull.signal(); // 被我消费掉一个，队列 not full 了，发个通知出去
            return x;
        } finally {
            lock.unlock();
        }
    }
}
```

> 1、我们可以看到，在使用 condition 时，必须先持有相应的锁。这个和 Object 类中的方法有相似的语义，需要先持有某个对象的监视器锁才可以执行 wait(), notify() 或 notifyAll() 方法。
>
> 2、ArrayBlockingQueue 采用这种方式实现了生产者-消费者，所以请只把这个例子当做学习例子，实际生产中可以直接使用 ArrayBlockingQueue

我们常用 obj.wait()，obj.notify() 或 obj.notifyAll() 来实现相似的功能，但是，它们是基于对象的监视器锁的。需要深入了解这几个方法的读者，可以参考我的另一篇文章《[深入分析 java 8 编程语言规范：Threads and Locks](http://hongjiev.github.io/2017/07/05/Threads-And-Locks-md/)》。而这里说的 Condition 是基于 ReentrantLock 实现的，而 ReentrantLock 是依赖于 AbstractQueuedSynchronizer 实现的。

在往下看之前，读者心里要有一个整体的概念。condition 是依赖于 ReentrantLock  的，不管是调用 await 进入等待还是 signal 唤醒，**都必须获取到锁才能进行操作**。

每个 ReentrantLock  实例可以通过调用多次 newCondition 产生多个 ConditionObject 的实例：

```java
final ConditionObject newCondition() {
    // 实例化一个 ConditionObject
    return new ConditionObject();
}
```

我们首先来看下我们关注的 Condition 的实现类 `AbstractQueuedSynchronizer` 类中的 `ConditionObject`。

```java
public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        // 条件队列的第一个节点
          // 不要管这里的关键字 transient，是不参与序列化的意思
        private transient Node firstWaiter;
        // 条件队列的最后一个节点
        private transient Node lastWaiter;
        ......
```

在上一篇介绍 AQS 的时候，我们有一个**阻塞队列**，用于保存等待获取锁的线程的队列。这里我们引入另一个概念，叫**条件队列**（condition queue），我画了一张简单的图用来说明这个。

> 这里的阻塞队列如果叫做同步队列（sync queue）其实比较贴切，不过为了和前篇呼应，我就继续使用阻塞队列了。记住这里的两个概念，**阻塞队列**和**条件队列**。

![condition-2](https://www.javadoop.com/blogimages/AbstractQueuedSynchronizer-2/aqs2-2.png)

> 这里，我们简单回顾下 Node 的属性：
>
> ```java
> volatile int waitStatus; // 可取值 0、CANCELLED(1)、SIGNAL(-1)、CONDITION(-2)、PROPAGATE(-3)
> volatile Node prev;
> volatile Node next;
> volatile Thread thread;
> Node nextWaiter;
> ```
>
> prev 和 next 用于实现阻塞队列的双向链表，这里的 nextWaiter 用于实现条件队列的单向链表

基本上，把这张图看懂，你也就知道 condition 的处理流程了。所以，我先简单解释下这图，然后再具体地解释代码实现。

1. 条件队列和阻塞队列的节点，都是 Node 的实例，因为条件队列的节点是需要转移到阻塞队列中去的；
2. 我们知道一个 ReentrantLock 实例可以通过多次调用 newCondition() 来产生多个 Condition 实例，这里对应 condition1 和 condition2。注意，ConditionObject 只有两个属性 firstWaiter 和 lastWaiter；
3. 每个 condition 有一个关联的**条件队列**，如线程 1 调用 `condition1.await()` 方法即可将当前线程 1 包装成 Node 后加入到条件队列中，然后阻塞在这里，不继续往下执行，条件队列是一个单向链表；
4. 调用`condition1.signal()` 触发一次唤醒，此时唤醒的是队头，会将condition1 对应的**条件队列**的 firstWaiter（队头） 移到**阻塞队列的队尾**，等待获取锁，获取锁后 await 方法才能返回，继续往下执行。

上面的 2->3->4 描述了一个最简单的流程，没有考虑中断、signalAll、还有带有超时参数的 await 方法等，不过把这里弄懂是这节的主要目的。

同时，从图中也可以很直观地看出，哪些操作是线程安全的，哪些操作是线程不安全的。 

这个图看懂后，下面的代码分析就简单了。

接下来，我们一步步按照流程来走代码分析，我们先来看看 wait 方法：

```java
// 首先，这个方法是可被中断的，不可被中断的是另一个方法 awaitUninterruptibly()
// 这个方法会阻塞，直到调用 signal 方法（指 signal() 和 signalAll()，下同），或被中断
public final void await() throws InterruptedException {
    // 老规矩，既然该方法要响应中断，那么在最开始就判断中断状态
    if (Thread.interrupted())
        throw new InterruptedException();

    // 添加到 condition 的条件队列中
    Node node = addConditionWaiter();

    // 释放锁，返回值是释放锁之前的 state 值
    // await() 之前，当前线程是必须持有锁的，这里肯定要释放掉
    int savedState = fullyRelease(node);

    int interruptMode = 0;
    // 这里退出循环有两种情况，之后再仔细分析
    // 1. isOnSyncQueue(node) 返回 true，即当前 node 已经转移到阻塞队列了
    // 2. checkInterruptWhileWaiting(node) != 0 会到 break，然后退出循环，代表的是线程中断
    while (!isOnSyncQueue(node)) {
        LockSupport.park(this);
        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
            break;
    }
    // 被唤醒后，将进入阻塞队列，等待获取锁
    if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT;
    if (node.nextWaiter != null) // clean up if cancelled
        unlinkCancelledWaiters();
    if (interruptMode != 0)
        reportInterruptAfterWait(interruptMode);
}
```

其实，我大体上也把整个 await 过程说得十之八九了，下面我们分步把上面的几个点用源码说清楚。

###### 1. 将节点加入到条件队列

addConditionWaiter() 是将当前节点加入到条件队列，看图我们知道，这种条件队列内的操作是线程安全的。

```java
// 将当前线程对应的节点入队，插入队尾
private Node addConditionWaiter() {
    Node t = lastWaiter;
    // 如果条件队列的最后一个节点取消了，将其清除出去
    // 为什么这里把 waitStatus 不等于 Node.CONDITION，就判定为该节点发生了取消排队？
    if (t != null && t.waitStatus != Node.CONDITION) {
        // 这个方法会遍历整个条件队列，然后会将已取消的所有节点清除出队列
        unlinkCancelledWaiters();
        t = lastWaiter;
    }
    // node 在初始化的时候，指定 waitStatus 为 Node.CONDITION
    Node node = new Node(Thread.currentThread(), Node.CONDITION);

    // t 此时是 lastWaiter，队尾
    // 如果队列为空
    if (t == null)
        firstWaiter = node;
    else
        t.nextWaiter = node;
    lastWaiter = node;
    return node;
}
```

上面的这块代码很简单，就是将当前线程进入到条件队列的队尾。

在addWaiter 方法中，有一个 unlinkCancelledWaiters() 方法，该方法用于清除队列中已经取消等待的节点。

当 await 的时候如果发生了取消操作（这点之后会说），或者是在节点入队的时候，发现最后一个节点是被取消的，会调用一次这个方法。

```java
// 等待队列是一个单向链表，遍历链表将已经取消等待的节点清除出去
// 纯属链表操作，很好理解，看不懂多看几遍就可以了
private void unlinkCancelledWaiters() {
    Node t = firstWaiter;
    Node trail = null;
    while (t != null) {
        Node next = t.nextWaiter;
        // 如果节点的状态不是 Node.CONDITION 的话，这个节点就是被取消的
        if (t.waitStatus != Node.CONDITION) {
            t.nextWaiter = null;
            if (trail == null)
                firstWaiter = next;
            else
                trail.nextWaiter = next;
            if (next == null)
                lastWaiter = trail;
        }
        else
            trail = t;
        t = next;
    }
}
```

###### 2. 完全释放独占锁

回到 wait 方法，节点入队了以后，会调用 `int savedState = fullyRelease(node);` 方法释放锁，注意，这里是完全释放独占锁（fully release），因为 ReentrantLock 是可以重入的。

> 考虑一下这里的 savedState。如果在 condition1.await() 之前，假设线程先执行了 2 次 lock() 操作，那么 state 为 2，我们理解为该线程持有 2 把锁，这里 await() 方法必须将 state 设置为 0，然后再进入挂起状态，这样其他线程才能持有锁。当它被唤醒的时候，它需要重新持有 2 把锁，才能继续下去。

```java
// 首先，我们要先观察到返回值 savedState 代表 release 之前的 state 值
// 对于最简单的操作：先 lock.lock()，然后 condition1.await()。
//         那么 state 经过这个方法由 1 变为 0，锁释放，此方法返回 1
//         相应的，如果 lock 重入了 n 次，savedState == n
// 如果这个方法失败，会将节点设置为"取消"状态，并抛出异常 IllegalMonitorStateException
final int fullyRelease(Node node) {
    boolean failed = true;
    try {
        int savedState = getState();
        // 这里使用了当前的 state 作为 release 的参数，也就是完全释放掉锁，将 state 置为 0
        if (release(savedState)) {
            failed = false;
            return savedState;
        } else {
            throw new IllegalMonitorStateException();
        }
    } finally {
        if (failed)
            node.waitStatus = Node.CANCELLED;
    }
}
```

> 考虑一下，如果一个线程在不持有 lock 的基础上，就去调用 condition1.await() 方法，它能进入条件队列，但是在上面的这个方法中，由于它不持有锁，release(savedState) 这个方法肯定要返回 false，进入到异常分支，然后进入 finally 块设置 `node.waitStatus = Node.CANCELLED`，这个已经入队的节点之后会被后继的节点”请出去“。
>
> ###### 3. 等待进入阻塞队列
>
> 释放掉锁以后，接下来是这段，这边会自旋，如果发现自己还没到阻塞队列，那么挂起，等待被转移到阻塞队列。
>
> ```java
> int interruptMode = 0;
> // 如果不在阻塞队列中，注意了，是阻塞队列
> while (!isOnSyncQueue(node)) {
>     // 线程挂起
>     LockSupport.park(this);
> 
>     // 这里可以先不用看了，等看到它什么时候被 unpark 再说
>     if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
>         break;
> }
> ```
>
> isOnSyncQueue(Node node) 用于判断节点是否已经转移到阻塞队列了：
>
> ```java
> // 在节点入条件队列的时候，初始化时设置了 waitStatus = Node.CONDITION
> // 前面我提到，signal 的时候需要将节点从条件队列移到阻塞队列，
> // 这个方法就是判断 node 是否已经移动到阻塞队列了
> final boolean isOnSyncQueue(Node node) {
> 
>     // 移动过去的时候，node 的 waitStatus 会置为 0，这个之后在说 signal 方法的时候会说到
>     // 如果 waitStatus 还是 Node.CONDITION，也就是 -2，那肯定就是还在条件队列中
>     // 如果 node 的前驱 prev 指向还是 null，说明肯定没有在 阻塞队列(prev是阻塞队列链表中使用的)
>     if (node.waitStatus == Node.CONDITION || node.prev == null)
>         return false;
>     // 如果 node 已经有后继节点 next 的时候，那肯定是在阻塞队列了
>     if (node.next != null) 
>         return true;
> 
>     // 下面这个方法从阻塞队列的队尾开始从后往前遍历找，如果找到相等的，说明在阻塞队列，否则就是不在阻塞队列
> 
>     // 可以通过判断 node.prev() != null 来推断出 node 在阻塞队列吗？答案是：不能。
>     // 这个可以看上篇 AQS 的入队方法，首先设置的是 node.prev 指向 tail，
>     // 然后是 CAS 操作将自己设置为新的 tail，可是这次的 CAS 是可能失败的。
> 
>     return findNodeFromTail(node);
> }
> 
> // 从阻塞队列的队尾往前遍历，如果找到，返回 true
> private boolean findNodeFromTail(Node node) {
>     Node t = tail;
>     for (;;) {
>         if (t == node)
>             return true;
>         if (t == null)
>             return false;
>         t = t.prev;
>     }
> }
> ```
>
> 回到前面的循环，isOnSyncQueue(node) 返回 false 的话，那么进到 `LockSupport.park(this);` 这里线程挂起。
>
> ###### 4. signal 唤醒线程，转移到阻塞队列
>
> 为了大家理解，这里我们先看唤醒操作，因为刚刚到 `LockSupport.park(this);` 把线程挂起了，等待唤醒。
>
> 唤醒操作通常由另一个线程来操作，就像生产者-消费者模式中，如果线程因为等待消费而挂起，那么当生产者生产了一个东西后，会调用 signal 唤醒正在等待的线程来消费。
>
> ```java
> // 唤醒等待了最久的线程
> // 其实就是，将这个线程对应的 node 从条件队列转移到阻塞队列
> public final void signal() {
>     // 调用 signal 方法的线程必须持有当前的独占锁
>     if (!isHeldExclusively())
>         throw new IllegalMonitorStateException();
>     Node first = firstWaiter;
>     if (first != null)
>         doSignal(first);
> }
> 
> // 从条件队列队头往后遍历，找出第一个需要转移的 node
> // 因为前面我们说过，有些线程会取消排队，但是可能还在队列中
> private void doSignal(Node first) {
>     do {
>           // 将 firstWaiter 指向 first 节点后面的第一个，因为 first 节点马上要离开了
>         // 如果将 first 移除后，后面没有节点在等待了，那么需要将 lastWaiter 置为 null
>         if ( (firstWaiter = first.nextWaiter) == null)
>             lastWaiter = null;
>         // 因为 first 马上要被移到阻塞队列了，和条件队列的链接关系在这里断掉
>         first.nextWaiter = null;
>     } while (!transferForSignal(first) &&
>              (first = firstWaiter) != null);
>       // 这里 while 循环，如果 first 转移不成功，那么选择 first 后面的第一个节点进行转移，依此类推
> }
> 
> // 将节点从条件队列转移到阻塞队列
> // true 代表成功转移
> // false 代表在 signal 之前，节点已经取消了
> final boolean transferForSignal(Node node) {
> 
>     // CAS 如果失败，说明此 node 的 waitStatus 已不是 Node.CONDITION，说明节点已经取消，
>     // 既然已经取消，也就不需要转移了，方法返回，转移后面一个节点
>     // 否则，将 waitStatus 置为 0
>     if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
>         return false;
> 
>     // enq(node): 自旋进入阻塞队列的队尾
>     // 注意，这里的返回值 p 是 node 在阻塞队列的前驱节点
>     Node p = enq(node);
>     int ws = p.waitStatus;
>     // ws > 0 说明 node 在阻塞队列中的前驱节点取消了等待锁，直接唤醒 node 对应的线程。唤醒之后会怎么样，后面再解释
>     // 如果 ws <= 0, 那么 compareAndSetWaitStatus 将会被调用，上篇介绍的时候说过，节点入队后，需要把前驱节点的状态设为 Node.SIGNAL(-1)
>     if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
>         // 如果前驱节点取消或者 CAS 失败，会进到这里唤醒线程，之后的操作看下一节
>         LockSupport.unpark(node.thread);
>     return true;
> }
> ```
>
> 正常情况下，`ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL)` 这句中，ws <= 0，而且 `compareAndSetWaitStatus(p, ws, Node.SIGNAL)` 会返回 true，所以一般也不会进去 if 语句块中唤醒 node 对应的线程。然后这个方法返回 true，也就意味着 signal 方法结束了，节点进入了阻塞队列。
>
> 假设发生了阻塞队列中的前驱节点取消等待，或者 CAS 失败，只要唤醒线程，让其进到下一步即可。
>
> ###### 5. 唤醒后检查中断状态
>
> 上一步 signal 之后，我们的线程由条件队列转移到了阻塞队列，之后就准备获取锁了。只要重新获取到锁了以后，继续往下执行。
>
> 等线程从挂起中恢复过来，继续往下看
>
> ```java
> int interruptMode = 0;
> while (!isOnSyncQueue(node)) {
>     // 线程挂起
>     LockSupport.park(this);
> 
>     if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
>         break;
> }
> ```
>
> 先解释下 interruptMode。interruptMode 可以取值为 REINTERRUPT（1），THROW_IE（-1），0
>
> - REINTERRUPT： 代表 await 返回的时候，需要重新设置中断状态
> - THROW_IE： 代表 await 返回的时候，需要抛出 InterruptedException 异常
> - 0 ：说明在 await 期间，没有发生中断
>
> 有以下三种情况会让 LockSupport.park(this); 这句返回继续往下执行：
>
> 1. 常规路径。signal -> 转移节点到阻塞队列 -> 获取了锁（unpark）
> 2. 线程中断。在 park 的时候，另外一个线程对这个线程进行了中断
> 3. signal 的时候我们说过，转移以后的前驱节点取消了，或者对前驱节点的CAS操作失败了
> 4. 假唤醒。这个也是存在的，和 Object.wait() 类似，都有这个问题
>
> 线程唤醒后第一步是调用 checkInterruptWhileWaiting(node) 这个方法，此方法用于判断是否在线程挂起期间发生了中断，如果发生了中断，是 signal 调用之前中断的，还是 signal 之后发生的中断。
>
> ```java
> // 1. 如果在 signal 之前已经中断，返回 THROW_IE
> // 2. 如果是 signal 之后中断，返回 REINTERRUPT
> // 3. 没有发生中断，返回 0
> private int checkInterruptWhileWaiting(Node node) {
>     return Thread.interrupted() ?
>         (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
>         0;
> }
> ```
>
> > Thread.interrupted()：如果当前线程已经处于中断状态，那么该方法返回 true，同时将中断状态重置为 false，所以，才有后续的 `重新中断（REINTERRUPT）` 的使用。
>
> 看看怎么判断是 signal 之前还是之后发生的中断：
>
> ```java
> // 只有线程处于中断状态，才会调用此方法
> // 如果需要的话，将这个已经取消等待的节点转移到阻塞队列
> // 返回 true：如果此线程在 signal 之前被取消，
> final boolean transferAfterCancelledWait(Node node) {
>     // 用 CAS 将节点状态设置为 0 
>     // 如果这步 CAS 成功，说明是 signal 方法之前发生的中断，因为如果 signal 先发生的话，signal 中会将 waitStatus 设置为 0
>     if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
>         // 将节点放入阻塞队列
>         // 这里我们看到，即使中断了，依然会转移到阻塞队列
>         enq(node);
>         return true;
>     }
> 
>     // 到这里是因为 CAS 失败，肯定是因为 signal 方法已经将 waitStatus 设置为了 0
>     // signal 方法会将节点转移到阻塞队列，但是可能还没完成，这边自旋等待其完成
>     // 当然，这种事情还是比较少的吧：signal 调用之后，没完成转移之前，发生了中断
>     while (!isOnSyncQueue(node))
>         Thread.yield();
>     return false;
> }
> ```
>
> > 这里再说一遍，即使发生了中断，节点依然会转移到阻塞队列。
>
> 到这里，大家应该都知道这个 while 循环怎么退出了吧。要么中断，要么转移成功。
>
> 这里描绘了一个场景，本来有个线程，它是排在条件队列的后面的，但是因为它被中断了，那么它会被唤醒，然后它发现自己不是被 signal 的那个，但是它会自己主动去进入到阻塞队列。
>
> ###### 6. 获取独占锁
>
> while 循环出来以后，下面是这段代码：
>
> ```java
> if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
>     interruptMode = REINTERRUPT;
> ```
>
> 由于 while 出来后，我们确定节点已经进入了阻塞队列，准备获取锁。
>
> 这里的 acquireQueued(node, savedState) 的第一个参数 node 之前已经经过 enq(node) 进入了队列，参数 savedState 是之前释放锁前的 state，这个方法返回的时候，代表当前线程获取了锁，而且 state == savedState了。
>
> 注意，前面我们说过，不管有没有发生中断，都会进入到阻塞队列，而 acquireQueued(node, savedState) 的返回值就是代表线程是否被中断。如果返回 true，说明被中断了，而且 interruptMode != THROW_IE，说明在 signal 之前就发生中断了，这里将 interruptMode 设置为 REINTERRUPT，用于待会重新中断。
>
> 继续往下：
>
> ```java
> if (node.nextWaiter != null) // clean up if cancelled
>     unlinkCancelledWaiters();
> if (interruptMode != 0)
>     reportInterruptAfterWait(interruptMode);
> ```
>
> 本着一丝不苟的精神，这边说说 `node.nextWaiter != null` 怎么满足。我前面也说了 signal 的时候会将节点转移到阻塞队列，有一步是 node.nextWaiter = null，将断开节点和条件队列的联系。
>
> 可是，`在判断发生中断的情况下，是 signal 之前还是之后发生的？` 这部分的时候，我也介绍了，如果 signal 之前就中断了，也需要将节点进行转移到阻塞队列，这部分转移的时候，是没有设置 node.nextWaiter = null 的。
>
> 之前我们说过，如果有节点取消，也会调用 unlinkCancelledWaiters 这个方法，就是这里了。
>
> ###### 7. 处理中断状态
>
> 到这里，我们终于可以好好说下这个 interruptMode 干嘛用了。
>
> - 0：什么都不做，没有被中断过；
> - THROW_IE：await 方法抛出 InterruptedException 异常，因为它代表在 await() 期间发生了中断；
> - REINTERRUPT：重新中断当前线程，因为它代表 await() 期间没有被中断，而是 signal() 以后发生的中断
>
> ```java
> private void reportInterruptAfterWait(int interruptMode)
>     throws InterruptedException {
>     if (interruptMode == THROW_IE)
>         throw new InterruptedException();
>     else if (interruptMode == REINTERRUPT)
>         selfInterrupt();
> }
> ```
>
> > 这个中断状态这部分内容，大家应该都理解了吧，不理解的话，多看几遍就是了。
>
> ###### 带超时机制的 await
>
> 经过前面的 7 步，整个 ConditionObject 类基本上都分析完了，接下来简单分析下带超时机制的 await 方法。
>
> ```java
> public final long awaitNanos(long nanosTimeout) 
>                   throws InterruptedException
> public final boolean awaitUntil(Date deadline)
>                 throws InterruptedException
> public final boolean await(long time, TimeUnit unit)
>                 throws InterruptedException
> ```
>
> 这三个方法都差不多，我们就挑一个出来看看吧：
>
> ```java
> public final boolean await(long time, TimeUnit unit)
>         throws InterruptedException {
>     // 等待这么多纳秒
>     long nanosTimeout = unit.toNanos(time);
>     if (Thread.interrupted())
>         throw new InterruptedException();
>     Node node = addConditionWaiter();
>     int savedState = fullyRelease(node);
>     // 当前时间 + 等待时长 = 过期时间
>     final long deadline = System.nanoTime() + nanosTimeout;
>     // 用于返回 await 是否超时
>     boolean timedout = false;
>     int interruptMode = 0;
>     while (!isOnSyncQueue(node)) {
>         // 时间到啦
>         if (nanosTimeout <= 0L) {
>             // 这里因为要 break 取消等待了。取消等待的话一定要调用 transferAfterCancelledWait(node) 这个方法
>             // 如果这个方法返回 true，在这个方法内，将节点转移到阻塞队列成功
>             // 返回 false 的话，说明 signal 已经发生，signal 方法将节点转移了。也就是说没有超时嘛
>             timedout = transferAfterCancelledWait(node);
>             break;
>         }
>         // spinForTimeoutThreshold 的值是 1000 纳秒，也就是 1 毫秒
>         // 也就是说，如果不到 1 毫秒了，那就不要选择 parkNanos 了，自旋的性能反而更好
>         if (nanosTimeout >= spinForTimeoutThreshold)
>             LockSupport.parkNanos(this, nanosTimeout);
>         if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
>             break;
>         // 得到剩余时间
>         nanosTimeout = deadline - System.nanoTime();
>     }
>     if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
>         interruptMode = REINTERRUPT;
>     if (node.nextWaiter != null)
>         unlinkCancelledWaiters();
>     if (interruptMode != 0)
>         reportInterruptAfterWait(interruptMode);
>     return !timedout;
> }
> ```
>
> 超时的思路还是很简单的，不带超时参数的 await 是 park，然后等待别人唤醒。而现在就是调用 parkNanos 方法来休眠指定的时间，醒来后判断是否 signal 调用了，调用了就是没有超时，否则就是超时了。超时的话，自己来进行转移到阻塞队列，然后抢锁。

###### 不抛出 InterruptedException 的 await

关于 Condition 最后一小节了。

```java
public final void awaitUninterruptibly() {
    Node node = addConditionWaiter();
    int savedState = fullyRelease(node);
    boolean interrupted = false;
    while (!isOnSyncQueue(node)) {
        LockSupport.park(this);
        if (Thread.interrupted())
            interrupted = true;
    }
    if (acquireQueued(node, savedState) || interrupted)
        selfInterrupt();
}
```

很简单，贴一下代码大家就都懂了，我就不废话了。

##### AbstractQueuedSynchronizer 独占锁的取消排队

这篇文章说的是 AbstractQueuedSynchronizer，只不过好像 Condition 说太多了，赶紧把思路拉回来。

接下来，我想说说怎么取消对锁的竞争？

上篇文章提到过，最重要的方法是这个，我们要在这里面找答案：

```java
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return interrupted;
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}
```

首先，到这个方法的时候，节点一定是入队成功的。

我把 parkAndCheckInterrupt() 代码贴过来：

```java
private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this);
    return Thread.interrupted();
}
```

这两段代码联系起来看，是不是就清楚了。

如果我们要取消一个线程的排队，我们需要在另外一个线程中对其进行中断。比如某线程调用 lock() 老久不返回，我想中断它。一旦对其进行中断，此线程会从 `LockSupport.park(this);` 中唤醒，然后 `Thread.interrupted();` 返回 true。

我们发现一个问题，即使是中断唤醒了这个线程，也就只是设置了 `interrupted = true` 然后继续下一次循环。而且，由于 `Thread.interrupted();`  会清除中断状态，第二次进 parkAndCheckInterrupt 的时候，返回会是 false。

所以，我们要看到，在这个方法中，interrupted 只是用来记录是否发生了中断，然后用于方法返回值，其他没有做任何相关事情。

所以，我们看外层方法怎么处理 acquireQueued 返回 false 的情况。

```java
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
static void selfInterrupt() {
    Thread.currentThread().interrupt();
}
```

所以说，lock() 方法处理中断的方法就是，你中断归中断，我抢锁还是照样抢锁，几乎没关系，只是我抢到锁了以后，设置线程的中断状态而已，也不抛出任何异常出来。调用者获取锁后，可以去检查是否发生过中断，也可以不理会。

------

来条分割线。有没有被骗的感觉，我说了一大堆，可是和取消没有任何关系啊。

我们来看 ReentrantLock 的另一个 lock 方法：

```java
public void lockInterruptibly() throws InterruptedException {
    sync.acquireInterruptibly(1);
}
```

方法上多了个 `throws InterruptedException` ，经过前面那么多知识的铺垫，这里我就不再啰里啰嗦了。

```java
public final void acquireInterruptibly(int arg)
        throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    if (!tryAcquire(arg))
        doAcquireInterruptibly(arg);
}
```

继续往里：

```java
private void doAcquireInterruptibly(int arg) throws InterruptedException {
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return;
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                // 就是这里了，一旦异常，马上结束这个方法，抛出异常。
                // 这里不再只是标记这个方法的返回值代表中断状态
                // 而是直接抛出异常，而且外层也不捕获，一直往外抛到 lockInterruptibly
                throw new InterruptedException();
        }
    } finally {
        // 如果通过 InterruptedException 异常出去，那么 failed 就是 true 了
        if (failed)
            cancelAcquire(node);
    }
}
```

既然到这里了，顺便说说 cancelAcquire 这个方法吧：

```java
private void cancelAcquire(Node node) {
    // Ignore if node doesn't exist
    if (node == null)
        return;
    node.thread = null;
    // Skip cancelled predecessors
    // 找一个合适的前驱。其实就是将它前面的队列中已经取消的节点都”请出去“
    Node pred = node.prev;
    while (pred.waitStatus > 0)
        node.prev = pred = pred.prev;
    // predNext is the apparent node to unsplice. CASes below will
    // fail if not, in which case, we lost race vs another cancel
    // or signal, so no further action is necessary.
    Node predNext = pred.next;
    // Can use unconditional write instead of CAS here.
    // After this atomic step, other Nodes can skip past us.
    // Before, we are free of interference from other threads.
    node.waitStatus = Node.CANCELLED;
    // If we are the tail, remove ourselves.
    if (node == tail && compareAndSetTail(node, pred)) {
        compareAndSetNext(pred, predNext, null);
    } else {
        // If successor needs signal, try to set pred's next-link
        // so it will get one. Otherwise wake it up to propagate.
        int ws;
        if (pred != head &&
            ((ws = pred.waitStatus) == Node.SIGNAL ||
             (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
            pred.thread != null) {
            Node next = node.next;
            if (next != null && next.waitStatus <= 0)
                compareAndSetNext(pred, predNext, next);
        } else {
            unparkSuccessor(node);
        }
        node.next = node; // help GC
    }
}
```

其实这个方法没什么好说的，一行行看下去就是了，节点取消，只要把 waitStatus 设置为 Node.CANCELLED，会有非常多的情况被从阻塞队列中请出去，主动或被动。

##### 再说 java 线程中断和 InterruptedException 异常

在之前的文章中，我们接触了大量的中断，这边算是个总结吧。如果你完全熟悉中断了，没有必要再看这节，本节为新手而写。

###### 线程中断

首先，我们要明白，中断不是类似 linux 里面的命令 kill -9 pid，不是说我们中断某个线程，这个线程就停止运行了。中断代表线程状态，每个线程都关联了一个中断状态，是一个 true 或 false 的 boolean 值，初始值为 false。

> Java 中的中断和操作系统的中断还不一样，这里就按照**状态**来理解吧，不要和操作系统的中断联系在一起

关于中断状态，我们需要重点关注 Thread 类中的以下几个方法：

```java
// Thread 类中的实例方法，持有线程实例引用即可检测线程中断状态
public boolean isInterrupted() {}

// Thread 中的静态方法，检测调用这个方法的线程是否已经中断
// 注意：这个方法返回中断状态的同时，会将此线程的中断状态重置为 false
// 所以，如果我们连续调用两次这个方法的话，第二次的返回值肯定就是 false 了
public static boolean interrupted() {}

// Thread 类中的实例方法，用于设置一个线程的中断状态为 true
public void interrupt() {}
```

我们说中断一个线程，其实就是设置了线程的 interrupted status 为 true，至于说被中断的线程怎么处理这个状态，那是那个线程自己的事。如以下代码：

```java
while (!Thread.interrupted()) {
   doWork();
   System.out.println("我做完一件事了，准备做下一件，如果没有其他线程中断我的话");
}
```

> 这种代码就是会响应中断的，它会在干活的时候先判断下中断状态，不过，除了 JDK 源码外，其他用中断的场景还是比较少的，毕竟 JDK 源码非常讲究。

当然，中断除了是线程状态外，还有其他含义，否则也不需要专门搞一个这个概念出来了。

如果线程处于以下三种情况，那么当线程被中断的时候，能自动感知到：

1. 来自 Object 类的 wait()、wait(long)、wait(long, int)，

   来自 Thread 类的 join()、join(long)、join(long, int)、sleep(long)、sleep(long, int)

   > 这几个方法的相同之处是，方法上都有: throws InterruptedException 
   >
   > 如果线程阻塞在这些方法上（我们知道，这些方法会让当前线程阻塞），这个时候如果其他线程对这个线程进行了中断，那么这个线程会从这些方法中立即返回，抛出 InterruptedException 异常，同时重置中断状态为 false。

2. 实现了 InterruptibleChannel 接口的类中的一些 I/O 阻塞操作，如 DatagramChannel 中的 connect 方法和 receive 方法等

   > 如果线程阻塞在这里，中断线程会导致这些方法抛出 ClosedByInterruptException 并重置中断状态。

3. Selector 中的 select 方法，参考下我写的 NIO 的文章

   > 一旦中断，方法立即返回

对于以上 3 种情况是最特殊的，因为他们能自动感知到中断（这里说自动，当然也是基于底层实现），**并且在做出相应的操作后都会重置中断状态为 false**。

那是不是只有以上 3 种方法能自动感知到中断呢？不是的，如果线程阻塞在 LockSupport.park(Object obj) 方法，也叫挂起，这个时候的中断也会导致线程唤醒，但是唤醒后不会重置中断状态，所以唤醒后去检测中断状态将是 true。



###### InterruptedException 概述

它是一个特殊的异常，不是说 JVM 对其有特殊的处理，而是它的使用场景比较特殊。通常，我们可以看到，像 Object 中的 wait() 方法，ReentrantLock 中的 lockInterruptibly() 方法，Thread 中的 sleep() 方法等等，这些方法都带有 `throws InterruptedException`，我们通常称这些方法为阻塞方法（blocking method）。

阻塞方法一个很明显的特征是，它们需要花费比较长的时间（不是绝对的，只是说明时间不可控），还有它们的方法结束返回往往依赖于外部条件，如 wait 方法依赖于其他线程的 notify，lock 方法依赖于其他线程的 unlock等等。

当我们看到方法上带有 `throws InterruptedException` 时，我们就要知道，这个方法应该是阻塞方法，我们如果希望它能早点返回的话，我们往往可以通过中断来实现。 

除了几个特殊类（如 Object，Thread等）外，感知中断并提前返回是通过轮询中断状态来实现的。我们自己需要写可中断的方法的时候，就是通过在合适的时机（通常在循环的开始处）去判断线程的中断状态，然后做相应的操作（通常是方法直接返回或者抛出异常）。当然，我们也要看到，如果我们一次循环花的时间比较长的话，那么就需要比较长的时间才能**感知**到线程中断了。

###### 处理中断

一旦中断发生，我们接收到了这个信息，然后怎么去处理中断呢？本小节将简单分析这个问题。

我们经常会这么写代码：

```java
try {
    Thread.sleep(10000);
} catch (InterruptedException e) {
    // ignore
}
// go on 
```

当 sleep 结束继续往下执行的时候，我们往往都不知道这块代码是真的 sleep 了 10 秒，还是只休眠了 1 秒就被中断了。这个代码的问题在于，我们将这个异常信息吞掉了。（对于 sleep 方法，我相信大部分情况下，我们都不在意是否是中断了，这里是举例）

AQS 的做法很值得我们借鉴，我们知道 ReentrantLock 有两种 lock 方法：

```java
public void lock() {
    sync.lock();
}

public void lockInterruptibly() throws InterruptedException {
    sync.acquireInterruptibly(1);
}
```

前面我们提到过，lock() 方法不响应中断。如果 thread1 调用了 lock() 方法，过了很久还没抢到锁，这个时候 thread2 对其进行了中断，thread1 是不响应这个请求的，它会继续抢锁，当然它不会把“被中断”这个信息扔掉。我们可以看以下代码：

```java
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        // 我们看到，这里也没做任何特殊处理，就是记录下来中断状态。
        // 这样，如果外层方法需要去检测的时候，至少我们没有把这个信息丢了
        selfInterrupt();// Thread.currentThread().interrupt();
}
```

而对于 lockInterruptibly() 方法，因为其方法上面有 `throws InterruptedException` ，这个信号告诉我们，如果我们要取消线程抢锁，直接中断这个线程即可，它会立即返回，抛出 InterruptedException 异常。

在并发包中，有非常多的这种处理中断的例子，提供两个方法，分别为响应中断和不响应中断，对于不响应中断的方法，记录中断而不是丢失这个信息。如 Condition 中的两个方法就是这样的：

```java
void await() throws InterruptedException;
void awaitUninterruptibly();
```

> 通常，如果方法会抛出 InterruptedException 异常，往往方法体的第一句就是：
>
> ```java
> public final void await() throws InterruptedException {
>     if (Thread.interrupted())
>         throw new InterruptedException();
>      ...... 
> }
> ```

熟练使用中断，对于我们写出优雅的代码是有帮助的，也有助于我们分析别人的源码。

##### 总结

这篇文章的信息量真的很大，如果你花了时间，还是没有看懂，那是我的错了。

欢迎大家向我提问，我不一定能每次都及时出现，我出现也不一定能解决大家的问题，欢迎探讨。

