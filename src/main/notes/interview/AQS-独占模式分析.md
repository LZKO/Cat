# AQS-独占模式分析

## 一、概述

AQS,全称为AbstractQueuedSynchronizer，位于java.util.concurrent.locks包下面，是我们常见的ReentrantLock、Semaphore、CountDownLatch、ThreadPoolExecutor这些处理并发的类的基础。AQS有两种模式，一种是独占模式，一种是共享模式，像ReentrantLock就是独占模式，因为一次只有一个线程可以竞争到锁，而像Semaphore就是共享模式，一次可以多个线程获取到资源，那本文就先介绍一下AQS的原理，以及独占模式的获取锁，释放锁的处理方式，本文会分析源码，文章可能比较臃肿。

## 二、AQS原理

![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200903114227863-1951907815.png)

AQS中核心的组件就两个，如下：

```
private volatile int state;
static final class Node {
    volatile int waitStatus;
    volatile Node prev;
    volatile Node next;
    volatile Thread thread;
}
```

- state，这个字段控制加锁，解锁，如果是0表示可以获取锁，如果大于0表示已经被别的线程获取
- Node,这个就是上图中画的队列，是一个双向链表，Node就是这个双向链表的节点，至于里面字段的意义，prev就是前一个，next就是后一个节点，剩下两个后面介绍。

上图处理过程介绍：

　　当state = 0的时候，线程1进来，通过CAS将state设置成1，加锁成功，之后线程2进来，先去查看state的值，发现state = 1,所以就把线程2放入双向链表中并阻塞，等线程1结束的时候，再把线程2唤醒。以上就是AQS的处理原理，下面就详细介绍一下AQS获取锁和释放锁的过程。

## 三、AQS如何保证可见性、有序性、原子性

- 原子性：这个就不用过多介绍了，通过state的值来控制获取锁，保证一次只有一个线程修改变量。
- 有序性：大家可以看下面**AQS获取锁**的例子，先lock，之后unlock，而这两个都是在修改state的值，而state是被volatile修饰的，由于volatile可以保证有序性，所以可以保证lock和unlock中间的代码不会和他们之外的代码发生指令重排。
- 可见性：和有序性一样，也是由于state是被volatile修饰的，volatile修饰的变量在被修改之前会加上storestore和loadstore内存屏障，变量后面加上store内存屏障，可以保证被lock和unlock夹在中间的代码修改的没有同步到主内存的变量，都同步到主内存，并且使得别的线程中变量失效。

## 四、AQS获取锁

这一块如果只分析AQS，不容易找到入口，那就从ReentranntLokc的lock方法开始分析，看如下的小例子。

```
public class ReentrantLockDemo implements Runnable{
     private static ReentrantLock lock = new ReentrantLock(true);
  
    @Override
    public void run() {
        while (true){
            lock.lock();
            try{
                System.out.println(Thread.currentThread().getName() + " get lock");
                Thread.sleep(1000);
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                lock.unlock();
            }
        }
    }
  
    public static void main(String[] args) {
        ReentrantLockDemo rtld = new ReentrantLockDemo();
        Thread thread1 = new Thread(rtld);
        Thread thread2 = new Thread(rtld);
        thread1.start();
        thread2.start();
    }
}
```

上面的代码是ReentrantLock使用的一个小的demo，从这个demo中可以看到，是调用lock方式进行加锁，那我们就分析一下这个lock方法。

```
private final Sync sync;
public void lock() {
        sync.lock();
    }
```

这里的sync，其实就是AbstractQueuedSynchronizer的子类，上面介绍了那么多并发类，他们如果想要使用AQS就是通过继承AbstractQueuedSynchronizer类实现的。由于ReentrantLock有公平锁和非公平锁的概念，上面例子中使用默认的方式就是非公平锁，下面就分析一下非公平锁加锁方式。

```
static final class NonfairSync extends Sync {
final void lock() {
            //尝试通过CAS的方式将state的状态从0该改成1
            if (compareAndSetState(0, 1))
　　　　　　　　　　//加锁成功，将当前排他锁的拥有线程改称当前线程
                setExclusiveOwnerThread(Thread.currentThread());
            else
　　　　　　　　　　//加锁失败，调用acquire，这个方法就是为了把线程给放到队列中，并且将线程给阻塞起来的
                acquire(1);
        }
}
```

这里主要分析一下acquire方法，进入AbstractQueuedSynchronizer#acquire()

```
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
```

这个方法是整个加锁的核心，也是整个AQS的核心，里面涉及到3个方法分别如下：

1. 第一个#tryAcquire()：该方法是尝试加锁，在AbstractQueuedSynchronizer中没有实现，由子类自己实现，采用模板方法模式。
2. 第二个#addWaiter()：这个方法就是将线程加入等待队列的。
3. 第三个#acquireQueued()：这个方法就是判断当前线程是否需要阻塞，如果不需要就一直空转，如果需要就阻塞。

OK,上面把三个方法简单的介绍了一下，下面详细分析一下每个方法。

#### 首先介绍：AbstractQueuedSynchronizer#tryAcquire()

```
protected boolean tryAcquire(int arg) {
     throw new UnsupportedOperationException();
 }
```

可以发现，这个方法如果调用就会抛出一个异常，所以需要子类自己实现这个方法。

我们看一下实现类ReentrantLock#NonfairSync#tryAcquire()

```
protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }

final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                //如果c = 0，表示当前没有线程占用锁，通过CAS加锁
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            //如果拥有当前锁的就是当前线程，表示这时是重入锁
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            //否则加锁失败，返回false
            return false;
        }
```

#### AbstractQueuedSynchronizer#addWaiter()

```
private Node addWaiter(Node mode) {
        //新建一个Node节点，用于一会加入双向链表尾端，这里的mode就是独占模式
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        //获取尾节点
        Node pred = tail;
        if (pred != null) {
            //将当前节点的前驱节点指针指向尾节点
            node.prev = pred;
            //通过CAS设置当前节点为尾节点
　　　　　　　if (compareAndSetTail(pred, node)) {
                //将之前的尾节点的后置节点指针指向当前节点 
                pred.next = node;
                return node;
            }
        }
        //这里为了处理链表中还没有节点的情况
        enq(node);
        return node;
    }
```

上面的过程大家可能不太理解的地方就是

```
if (compareAndSetTail(pred, node)) {
```

我们看看这个CAS操作的代码

```
private final boolean compareAndSetTail(Node expect, Node update) {
    return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
}
```

这里是调用unsafe这个魔法类，是一个CAS操作，第一个参数this表示当前对象，第二个tailOffset表示偏移量，第三个表示期待值，第四个就是要更新的值。有疑惑的地方就是这个tailOffset，我们看看这个怎么来的。

```
private static final long tailOffset;   
 tailOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
```

　上面的代码就是获取tailoffset的，调用#objectFieldOffset()方法，作用就是获取AbstractQueuedSynchronizer这个类中的tail字段相对于当前对象的基准地址的偏移量，这句话有点拗口，其实意思就是AbstractQueuedSynchronizer这个类也会初始化，然后在内存中有一个地址，然后这个对象中一个tail字段，tail字段也会有一个地址，返回的tailoffset就是tail地址相比于对象地址的偏移量，是一个long类型的

　　　　![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200903155409917-845236517.png)

 上图的baseAddress就是对象的地址，valueOffset就可以理解成tail字段相对于对象的偏移量。

代码分析到这里有点发散了，我们重新回到addWaiter()，分析一下里面调用的一个方法#enq()

```
private Node enq(final Node node) {
        for (;;) {
            //获取尾节点
            Node t = tail;
            if (t == null) { // Must initialize
                //设置首节点head字段，同上面分析的设置尾节点类似
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

#### addWaiter()方法总结　

- 将当前线程封装成Node节点，之后将Node节点挂到双向链表末尾
- 如果双向链表中一个节点还没有，就新建一个空节点，然后将当前节点挂到空节点上

#### #acquireQueued()

这个方法是以上3个方法中最复杂的，参数中的node就是刚刚新加入队列的node

```
final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                //获取当前节点的前一个节点
                final Node p = node.predecessor();
                //如果前一个节点是首节点，那本节点就是第二个节点（注意：双向链表的首节点其实是一个虚拟节点，没有实际的作用）
                //双向链表中可能有很多的节点，每个节点就是一个要竞争锁的线程，那要从哪一个节点开始解锁呢？答案就在这里
                //从第二个节点开始解锁，后面的线程暂时等候
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    //如果获取到锁，当前死循环结束
                    return interrupted;
                }
                //如果当前节点的前一个节点不是首节点，那就做一点处理，然后把当前线程给挂起
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

　这里再说明一下，之前我其实也有个疑惑，链表中那么多的节点，到底要激活哪个节点，让他获取锁呢？我之前的想法是由于是非公平锁，那队列中的每个节点应该都可以参与竞争，其实不然，这里队列中可以获取锁的其实从前到后依次唤醒的，那么问题就来了，这样一来不就变成公平的了吗，其实也不然，假如现在我要激活队列中的一个节点，这时从外面又有一个线程请求进来，如果按照公平的原则，这个线程是没有竞争锁的资格的，但是由于是非公平锁，新进来的线程依然可以竞争锁。

 

下面看一下#shouldParkAfterFailedAcquire()方法

```
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        //前一个节点的状态
        int ws = pred.waitStatus;
        //如果前一个节点是signal状态，就直接返回，然后挂起线程，这个状态主要是为了释放锁之后，如果首节点是Signal状态才会去激活首节点后面的节点        //设置这个最主要的作用就是让前一个节点唤醒后一个节点
        //为什么要寻找这个状态，因为节点入队的时候一开始状态是0，然后每个节点都会把他前面的一个节点的状态改成signal
        //这样只要发现前一个节点状态为signal = -1,就说明他后面一定有一个节点。
        if (ws == Node.SIGNAL)
            return true;
        if (ws > 0) {
           
            do {
                //当waitStatus > 0，说明当前节点处于取消（CANCELLED）状态，就是这个节点的线程要么获取锁超时了
                //要么就是被中途中断了，这两个情况后面我会再解释
                //这段代码的作用就是把所有处于取消状态的节点干掉
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
           
            //设置当前节点的前一个节点状态为-1
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }
```

#### 该方法总结

- 总的来说，这个方法的一个核心的作用就是把当前节点的前一个节点的状态改成 -1，只要这个成功，就返回
- 当前节点的前一个节点可能已经取消了，什么意思呢？其实获取锁失败后处理方法不只有acquire()，还有另外两个，分别是acquireInterruptibly(),tryAcquireNanos(),前一个方法就是当线程阻塞挂起之后不允许中断，如果中断了就会抛出异常，而且还会把那个节点的状态更改为CANCELLED状态。后面一个方法是规定要给锁等待超时时间，如果获取锁超时了，节点也会被设置为CANCELLED状态。

当把前一个节点设置为signal状态成功之后，就会调用如下方法#parkAndCheckInterrupt()

```
private final boolean parkAndCheckInterrupt() {
     LockSupport.park(this);
     return Thread.interrupted();
 }
```

park方法是把当前线程给挂起了，线程执行到这里停止了，那这个return其实这个时候是无法返回的，我们再回到我们最初的acquireQueue方法看看

```
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

可以发现这里是一个死循环，死循环结束或者暂停有两个办法：

1. 第一个就是当前节点的前一个节点是首节点，然后当前节点获取锁成功，然后返回
2. 第二个调用parkAndCheckInterrupt()方法的park方法，将当前死循环阻塞，大家可以看一下，如果当前节点的前一个节点不是首节点，他要干的事情就是把当前节点的前一个节点的状态改成signal，也就是说，这个for循环一般就只需要循环2次就可以阻塞了。

那问题来了，线程阻塞到这里了，那怎么唤醒呢？这个先不要着急，先看如果这个线程被唤醒了，他干什么事情，当线程被唤醒了之后，意味着死循环又开始执行了，这个时候就讲究了，如果唤醒的线程的前一个节点不是首节点，这个死循环还会转一圈再阻塞，所以后面在分析释放锁的时候就可以看到，他其实唤醒的就是第二个节点。

再分析#shouldParkAfterFailedAcquire这个方法时候，提到会把所有处于CANCELLED状态的节点给干掉，那问题来了，什么时候把节点的状态改成的CANCELLED状态，其实就再finally里面的#cancelAcquire()方法，这个finally在什么情况下会执行呢？有两种可能：

1. 上面的for执行结束，线程正常获取到锁
2. 第二种就是上面我分析的情况了，acquireInterruptibly(),tryAcquireNanos()，但是对于当前正在分析的方法不会出现这种情况，也就是说对于当前正在分析的acquireQueued方法来说，这个finally其实是没有意义的。

\#cancelAcquire()这个方法留在之后的文章分析，因为对于本篇文章分析的方法是无意义的。

## 五、加锁过程总结

以上把加锁的过程整体给过了一篇，是不是有点晕，我第一次看这代码也是一头雾水，多看几遍就好了。下面就把整个过程重新给串一下。

1. 通过CAS加锁，失败，进入第2步
2. 重新尝试获取锁，失败，将线程封装成Node放入队列中，无限重试，直到入队成功为止
3. 判断当前节点的前一个节点是不是首节点，如果是，无限重试获取锁，直到成功，如果不是首节点，进入第4步
4. 将当前线程前面处于CANCELLED状态的节点给干掉，然后更改线程节点的前一个节点的状态为signal，无限重试，直到更改成功，然后将当前线程挂起

## 六、AQS解锁

解锁过程的分析要比加锁过程的分析愉快的多，下面看一下解锁过程的代码

首先看ReentrantLock#unlock方法

```
public void unlock() {
        sync.release(1);
    }
public final boolean release(int arg) {
        //尝试解锁，这个方法在AbstractQueuedSynchronizer中也没有实现，需要子类自己实现
        if (tryRelease(arg)) {
            Node h = head;
            //这里为什么要判断waitStatus != 0和里面的unparkSuccessor有关系，之后分析
            if (h != null && h.waitStatus != 0)
                //锁资源已经释放，这里就去唤醒队列首节点下一个节点
                unparkSuccessor(h);
            return true;
        }
        return false;
    }
```

进入ReentrantLock#tryRelease()方法

```
protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            //直接将AQS的state设置为0，解锁成功
            setState(c);
            return free;
        }
```

这个方法没什么好说的，就是直接更改状态，这里没有使用CAS更改，因为并不需要先判断state是不是为1，因为当前线程拥有锁，那state一定为1

进入AbstractQueuedSynchronizer#unparkSuccessor()方法

```
//参数解释:这里的node是首节点
private void unparkSuccessor(Node node) {
        int ws = node.waitStatus;
        if (ws < 0)
            //如果首节点状态小于0，先更新为0，这也是上面分析release方法的时候，那里要判断不为0的原因
            //如果首节点状态为0，说明正好有一个线程解锁，并且在唤醒队列中的节点
            //如果在唤醒队列中节点之前有另一个线程进来获取到锁，并且很快退出了，按照正常的逻辑，他也要去唤醒
            //这个时候就会有两个线程在唤醒队列中的节点，可能就会出问题，所以release方法中判断了waitStatus!=0
            compareAndSetWaitStatus(node, ws, 0);

        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            //如果首节点的下一个节点状态大于0，说明这个节点线程被CANCELLED了，那就要找到首节点下一个没有被CANCELLED的
            //注意这里是从后往前找，没搞明白为什么
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null)
            //如果首节点的下一个节点不是null，且不是CANCELLED状态，直接唤醒
            LockSupport.unpark(s.thread);
    }
```

至此，解锁过程也分析完成，AQS里面还有几种其他的加锁方式，就是上面介绍的，禁止中断的方式呀，或者设置超时时间的方式呀，其实和上面分析的大同小异，搞明白了这个，剩下的其实就很容易看懂了。

## 七、总结 

本篇文章分析了AQS的加锁解锁过程，只分析了独占模式中无中断和超时的方式，另外两种方式有时间再分析。

## 八、参考

[AQS-独占模式分析](https://www.cnblogs.com/gunduzi/p/13608203.html)



## END