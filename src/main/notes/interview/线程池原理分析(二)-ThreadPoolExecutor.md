# 线程池原理分析(二)-ThreadPoolExecutor

## 一、概述

[上篇文章](https://www.cnblogs.com/gunduzi/p/13673877.html)简单介绍了和线程池相关的类和接口，本文就详细介绍下其中一个类ThreadPoolExecutor，该类实现了线程池的功能，其基本原理就是使用一个HashSet集合存放Worker，Worker也实现了Runnable接口，重写了run方法，所以可认为这就是一个线程。如果设置线程池线程数量，并且让线程池执行的任务过多，这时候由于线程池中的线程不够用，就需要将暂时没有处理的任务给保存起来，待有线程空闲了再处理，一般常用的存储容器就是阻塞队列，整体的设计思想就是上面介绍的，里面还有很多的细节，下面分析源码的时候会介绍。

## 二、线程池状态

先介绍一下线程池可以处于的几种状态，了解了这些有助于理解代码，看过Thread源码的，应该了解Thread有生命周期，线程池也一样，也有自己的生命周期，下面就介绍一下。

```
//后面会单独介绍
private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
//Ieteger的长度是32，所以这个值为29
private static final int COUNT_BITS = Integer.SIZE - 3;
//线程池最大线程数量，二进制相当于29个1，大约是5亿
private static final int CAPACITY   = (1 << COUNT_BITS) - 1;
 
//下面的几个表示线程池的状态
 
 
//运行状态
private static final int RUNNING    = -1 << COUNT_BITS;
//该状态不在接受新的任务，但是会把阻塞队列中的任务处理完，之后关闭线程池
private static final int SHUTDOWN   =  0 << COUNT_BITS;
//该状态不在接受新的任务，并且会把阻塞队列中的线程丢弃，之后关闭线程池
private static final int STOP       =  1 << COUNT_BITS;
//中间状态,当线程池线程数为0，队列中任务数为0，处于该状态
private static final int TIDYING    =  2 << COUNT_BITS;
//线程池关闭后的最终状态
private static final int TERMINATED =  3 << COUNT_BITS;
 
 
//下面几个方法是操作ctl的  
 
//获取线程池所处的状态
private static int runStateOf(int c)     { return c & ~CAPACITY; }
//获取线程池工作线程数量
private static int workerCountOf(int c)  { return c & CAPACITY; }
//做一个或运算
private static int ctlOf(int rs, int wc) { return rs | wc; }
```

ctl：是一个32位的Integer型的数字，高3位表示线程池的状态，剩下的29位表示线程个数。在上面代码中，其中runStateOf方法和workerCountOf方法就是获取线程状态和线程数量的。

线程状态代码中已经注释了，下面就再详细介绍一下：

1. **RUNNING**：能接受新提交的任务，并且也能处理阻塞队列中的任务；
2. **SHUTDOWN**：关闭状态，不再接受新提交的任务，但却可以继续处理阻塞队列中已保存的任务。在线程池处于 RUNNING 状态时，调用 shutdown()方法会使线程池进入到该状态。（finalize() 方法在执行过程中也会调用shutdown()方法进入该状态）；
3. **STOP**：不能接受新任务，也不处理队列中的任务，会中断正在处理任务的线程。在线程池处于 RUNNING 或 SHUTDOWN 状态时，调用 shutdownNow() 方法会使线程池进入到该状态；
4. **TIDYING**：如果所有的任务都已终止了，workerCount (有效线程数) 为0，线程池进入该状态后会调用tryTerminate() 方法进入TERMINATED 状态。
5. **TERMINATED**：在tryTerminate() 方法执行完后进入该状态，默认terminated()方法中什么也没有做。进入TERMINATED的条件如下：

​          **I**：线程池不是RUNNING状态；

​          **II**：线程池状态不是TIDYING状态或TERMINATED状态；

​          **III**：如果线程池状态是SHUTDOWN并且workerQueue为空；

​          **IV**：workerCount为0；

​          **V**：设置TIDYING状态成功。

　　下图为线程池的状态转换过程：

  ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200915200358601-2105955235.png)

 　　　　　　　                      来源：[深入理解Java线程池：ThreadPoolExecutor](http://www.ideabuffer.cn/2017/04/04/深入理解Java线程池：ThreadPoolExecutor/)

## 三、构造方法

```
public ThreadPoolExecutor(int corePoolSize,
                          int maximumPoolSize,
                          long keepAliveTime,
                          TimeUnit unit,
                          BlockingQueue<Runnable> workQueue,
                          ThreadFactory threadFactory,
                          RejectedExecutionHandler handler) {
    if (corePoolSize < 0 ||
        maximumPoolSize <= 0 ||
        maximumPoolSize < corePoolSize ||
        keepAliveTime < 0)
        throw new IllegalArgumentException();
    if (workQueue == null || threadFactory == null || handler == null)
        throw new NullPointerException();
    this.acc = System.getSecurityManager() == null ?
            null :
            AccessController.getContext();
    this.corePoolSize = corePoolSize;
    this.maximumPoolSize = maximumPoolSize;
    this.workQueue = workQueue;
    this.keepAliveTime = unit.toNanos(keepAliveTime);
    this.threadFactory = threadFactory;
    this.handler = handler;
}
```

- **corePoolSize**：核心线程数
- **maximumPoolSize**：最大线程数
- **keepAliveTime**：线程空闲时在线程池中存活时间，当线程数大于核心线程数，在默认情况下，非核心线程在没有任务执行的情况下，存活的最大时间，通过allowsCoreThreadTimeOut()方法可以设置核心线程空闲时是否有最大存活时间

- **workQueue**：阻塞队列，用于存放处理不过来的任务，不同的场景往往要使用不同的阻塞队列，比如：

1. 1. 当不希望任务在队列中阻塞，而是直接交给线程去处理，可以使用SynchronousQueue，该队列不会存储元素，当一个线程执行put操作的时候，需要另一个线程执行take操作，使用该队列往往会把线程池设置成无界线程池，不然如果线程用完了，就会拒绝提交新的任务
   2. 对于像大数据中的任务，使用多线程拆分处理，可以减少执行时间，这种场景任务执行时间往往都很久，可以使用有界队列，比如ArrayBlockingQueue，并且可以适当调大maximumPoolSize，让CPU处于相对满负荷状态，不过如果设置太大，线程之间频繁的上下文切换开销也会变大
   3. 对于像延时执行的场景，提交的任务不立即执行，而是延迟一段时间执行，可以考虑使用DelayedQueue
   4. 对于像web服务这种，防止突然的并发太高，可以使用无界队列LinkedBlockingQueue，使用该队列后maximumPoolSize参数就失效了（一会会说明原因）

- **threadFactory**：新建线程的工厂类，可以自己实现，只要实现ThreaFactory接口即可

```
public interface ThreadFactory {
 
    Thread newThread(Runnable r);
}
```

　　当然，也可以不传，使用默认工厂类即可，在Executors中有如下实现

```
static class DefaultThreadFactory implements ThreadFactory {
      private static final AtomicInteger poolNumber = new AtomicInteger(1);
      private final ThreadGroup group;
      private final AtomicInteger threadNumber = new AtomicInteger(1);
      private final String namePrefix;
 
      DefaultThreadFactory() {
          SecurityManager s = System.getSecurityManager();
          group = (s != null) ? s.getThreadGroup() :
                                Thread.currentThread().getThreadGroup();
          namePrefix = "pool-" +
                        poolNumber.getAndIncrement() +
                       "-thread-";
      }
 
      public Thread newThread(Runnable r) {
          Thread t = new Thread(group, r,
                                namePrefix + threadNumber.getAndIncrement(),
                                0);
          if (t.isDaemon())
              t.setDaemon(false);
          if (t.getPriority() != Thread.NORM_PRIORITY)
              t.setPriority(Thread.NORM_PRIORITY);
          return t;
      }
  }
```

- **handler**：饱和策略，当线程数量已经达到maximumPoolSize，并且队列也满了，这时如果再有新的任务提交进来，就会直接走饱和策略，饱和策略可以自己实现，只要实现如下接口即可

```
public interface RejectedExecutionHandler {
    void rejectedExecution(Runnable r, ThreadPoolExecutor executor);
}
```

也可以使用线程池中已经定义好的饱和策略，至于在代码中什么时候调用rejectedExecution()方法，后面会看到。

![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200915210715002-328253403.png)

 　　　　　　　　　　　                            来源：[Java线程池实现原理及其在美团业务中的实践](https://tech.meituan.com/2020/04/02/java-pooling-pratice-in-meituan.html)

## 四、execute(Runnable command)方法

该方法是线程池线程执行任务的入口，下面举一个简单的例子。

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
ExecutorService pool = new ThreadPoolExecutor(2, 5, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(5),new ThreadPoolExecutor.CallerRunsPolicy());

pool.execute(()->{
                System.out.println(Thread.currentThread().getName());
            });
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

通过上面的例子可以看出，pool最后调用execute方法执行了Runnable任务，下面就分析一下execute方法的代码。

```
public void execute(Runnable command) {
    //如果传入的任务为null，抛出异常
    if (command == null)
        throw new NullPointerException();
    //获取ctl的值
    int c = ctl.get();
    //如果工作线程数量小于核心线程
    if (workerCountOf(c) < corePoolSize) {
        //执行addWorker方法，就是新建一个新的线程加入线程池
        if (addWorker(command, true))
            return;
        //如果addWorker成功就直接返回，失败才会执行到这里
        c = ctl.get();
    }
 
    //执行到这里说明工作线程大于等于核心线程数
    //如果线程池处于RUNNING状态
    //将任务加入到队列
    if (isRunning(c) && workQueue.offer(command)) {
        //加入队列成功，重新检查一下
        int recheck = ctl.get();
        //如果当前线程池不处于RUNNING状态，就将刚刚加入阻塞队列的任务移除
        //这里二次判断的原因就是为了防止在这个时刻别的线程执行了shutdown()等方法
        if (! isRunning(recheck) && remove(command))
            //执行饱和策略，这里就是在构造方法传入进来的饱和策略
            reject(command);
 
        //这里判断线程池中线程数为0，不明白为什么做这个判断，除非核心线程也设置了keepAliveTime，然后都超时了
        //如果上面的猜测正确，这里向线程池添加了一个线程，为了防止加入队列的任务没有线程处理
        else if (workerCountOf(recheck) == 0)
 
           //这里addWorker第一个参数为空的原因是上面已经把任务放入到队列中，所以就不需要传入任务
           //第二个参数为false，将线程池的有限线程数量的上限设置为maximumPoolSize
            addWorker(null, false);
    }
    //执行到这里说明：线程数量大于等于核心线程数，并且队列也已经满了
    //这时新增线程，直到达到maximumPoolSize
    else if (!addWorker(command, false))
       //如果新增线程失败，执行饱和策略
        reject(command);
}  
```

以上过程梳理成流程图如下，借用美团的大佬们画的。

​    ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200915223126558-1655495394.png)

​               图片来源：[Java线程池实现原理及其在美团业务中的实践](https://tech.meituan.com/2020/04/02/java-pooling-pratice-in-meituan.html)

从上图中可知，只有当阻塞队列满了之后，才会继续新增线程，直到达到maximumPoolSize，但是如果使用无界队列，就不会继续增加线程，这可能会导致队列中堆积大量的任务，而线程池又无法增加线程加快处理速度，如果阻塞队列里面塞了很多大对象，最终可能会OOM。

## 五、addWorker(Runnable firstTask, boolean core)方法

该方法就是新增线程到线程池的方法，第一个参数表示新增线程要处理的第一个任务，第二个参数表示当前用于判断有限线程数量是corePoolSize还是maximumPoolSize。既然要介绍addWorker，那首先要知道Worker是啥，在概述中提到，其实现了Runnable接口，下面就具体看一下其代码。

#### Worker类

```
private final class Worker extends AbstractQueuedSynchronizer implements Runnable{
       /** Thread this worker is running in.  Null if factory fails. */
        final Thread thread;
        /** Initial task to run.  Possibly null. */
        Runnable firstTask;
        /** Per-thread task counter */
        volatile long completedTasks;
 
 
        Worker(Runnable firstTask) {
           //初始化状态为-1，表示刚刚创建的线程还没有执行不能被中断
           //因为若要中断当前线程需要获取锁，而要获取锁，需要state从0变成1
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            //通过工厂类创建新线程
            this.thread = getThreadFactory().newThread(this);
        }
        //实现了run方法，之后会分析这个方法
        public void run() {
            runWorker(this);
        }
 
/**************************下面的方法都是和加锁解锁相关的***********************************************/
        //是否拥有独占锁      
        protected boolean isHeldExclusively() {
            return getState() != 0;
        }
        //尝试加锁
        protected boolean tryAcquire(int unused) {
            //通过CAS尝试将state状态从0变成1，这里就说明了，当state=-1的时候，是无法加锁成功的
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }
        //尝试解锁
        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }
 
        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }
 
}
```

从代码中可以看出，这个类有三个属性

- state：控制加锁解锁
- firstTask：该线程要执行的第一个任务，如果为null，就从阻塞队列中获取任务执行
- completedTasks：表示该线程总共执行的任务数量，主要用于记录监控使用

继承了AbstractQueuedSynchronizer，相当于自己重新写了个锁，为啥不用现成的，比如ReentrantLock，因为ReentrantLock是可重入锁，这个类实现的不可重入锁，因为state只有三种状态-1，0，1，那为什么要设计成不可重入呢？主要是为了防止线程在运行过程被中断，比如线程在执行的某个任务，在该任务的run方法中调用了setMaximumPoolSize()，当重新设置的最大线程数小于当前正常运行的线程数时，就会中断。setMaximumPoolSize()代码如下：

```
public void setMaximumPoolSize(int maximumPoolSize) {
    if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
        throw new IllegalArgumentException();
    this.maximumPoolSize = maximumPoolSize;
    //重新设置的最大线程数小于当前线程池中的线程数
    if (workerCountOf(ctl.get()) > maximumPoolSize)<br>            //中断空闲线程
        interruptIdleWorkers();
}
```

这样设置成不可重入锁，确保Worker处于两种状态，要么是运行状态，要么是空闲状态，只有当是空闲状态时才可以中断线程，这么说其实不太准确，使用shutdonwNow()方法的时候，不管是否处于运行状态还是空闲状态，都会直接中断，不过执行shutdownNow()方法的时候，就表示要结束线程池了，怎么中断都无所谓了。

 

Ok，有了上面的知识，我们来看addWorker方法

```
private boolean addWorker(Runnable firstTask, boolean core) {
       //外层for循环标志位
        retry:
        for (;;) {
            int c = ctl.get();
            //获取线程池状态
            int rs = runStateOf(c);
 
            // Check if queue empty only if necessary.
            //当前状态大于等于SHUTDOWN成立，然后再判断如果等于SHUTDOWN
            //因为SHUTDOWN状态不在接受新任务，所以如果firstTask != null,直接返回失败
            //同样因为SHUTDOWN状态会把队列中任务处理完，所以只要队列不为空，就不会返回失败，如果队列为空，就直接返回
            if (rs >= SHUTDOWN &&
                ! (rs == SHUTDOWN &&
                   firstTask == null &&
                   ! workQueue.isEmpty()))
                return false;
 
            for (;;) {
                int wc = workerCountOf(c);
                //如果当前线程池的中线程大于最大容量，直接返回
                //这里的core就是参数中传入进来的，如果在调用该方法时判断线程数小于核心线程数传的是true，否则就是false
                if (wc >= CAPACITY ||
                    wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                //通过CAS将c增加1，表示工作线程数增加了一个
                if (compareAndIncrementWorkerCount(c))
                    //如果成功，结束for循环
                    break retry;
                //重新获取
                c = ctl.get();  // Re-read ctl
                //如果ctl被别的线程改变
                if (runStateOf(c) != rs)
                    //跳到外层for循环，检测当前线程池是否被关闭了
                    //这里使用两层for循环的原因如下：
                    //内层for循环只要是为了确保CAS成功
                    //外层for循环主要为了判断线程池所处的状态
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }
        }
 
        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            //初始化一个Worker
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                //这里加锁，这个锁是线程池的锁，不是worker的锁，加锁的目的为了保证对set集合的操作是线程安全的
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int rs = runStateOf(ctl.get());
                    //如果是RUNNING状态
                   //或者是SHUTDOWN状态，firstTask为null，要求为null的原因就是SHUTDOWN状态不再接受新任务
                    if (rs < SHUTDOWN ||
                        (rs == SHUTDOWN && firstTask == null)) {
                        //线程刚创建没有启动，应该处于NEW状态，没有alive
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        //加入set集合
                        workers.add(w);
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                //加入set集合成功
                if (workerAdded) {
                    //启动Worker，这里会调用Worker中的run方法
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            //如果启动worker失败
            if (! workerStarted)
                //将worker移除等后续操作
                addWorkerFailed(w);
        }
        return workerStarted;
    }
```

具体解释看注释，以上流程如下：

​    ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200916083528940-1970970808.png)

​                                图片来源：[Java线程池实现原理及其在美团业务中的实践](https://tech.meituan.com/2020/04/02/java-pooling-pratice-in-meituan.html)

简单来说这个方法做了两件事：

- 修改ctl大小，意思是重新设置线程池中线程数量
- 新增线程并放入集合中，然后启动线程

在调用t.start()方法的时候，会调用worker中的run方法，下面就分析一下runWorker方法。

## 六、runWorker(Worker w)方法

这个方法的作用是执行初始化workder的时候传入任务，之后消费阻塞队列，执行阻塞队列中的任务。

```
final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        //worker初始化时state为-1，这里先解锁是把state变成0，表示此时worker线程可中断
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            //如果task为null，就从阻塞队列获取，如果阻塞队列也获取失败，结束while循环
            while (task != null || (task = getTask()) != null) {
                //对worker加锁，表示当前线程正在运行，不可中断
                w.lock();
                //如果线程池状态大于STOP
                //或者线程被中断，中断当前线程，中断不影响运行，就看task.run是否响应中断
                //如果task.run不响应中断，当该线程下次去阻塞队列获取任务时，需要获取锁，如果获取锁的时候发现被别的线程占用着锁，当前线程挂起，这时候由于线程被中断了，就会直接退出
                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() &&
                      runStateAtLeast(ctl.get(), STOP))) &&
                    !wt.isInterrupted())
                    wt.interrupt();
                try {
                    //线程池中没有实现，交给子类实现
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    //更新worker中completedTasks字段，表示又成功执行了一个任务
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            //当while循环退出的时候会执行到这里
           //一会会分析getTask方法，分析完之后就明白有几种情况会导致while循环退出
            processWorkerExit(w, completedAbruptly);
        }
    }
```

这个方法整体执行比较简单，就是不停的从阻塞队列中获取任务执行，里面有两个方法需要分析一下，一个是getTask，从阻塞队列获取任务，另一个是processWorkerExit，该方法会回收Worker，就是回收线程。

以上过程流程如下：

​    ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200916091317234-1512339485.png)

​        图片来源：[Java线程池实现原理及其在美团业务中的实践](https://tech.meituan.com/2020/04/02/java-pooling-pratice-in-meituan.html)

## 七、getTask()方法

```
private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?
 
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);
 
            // Check if queue empty only if necessary.
            //如果处在SHUTDOWN状态，并且阻塞队列为空，直接返回null，结束while循环
            //或者处于STOP状态，直接返回null，表示不再处理阻塞队列中的任务
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                //工作线程减1
                decrementWorkerCount();
                return null;
            }
 
            int wc = workerCountOf(c);
 
            // Are workers subject to culling?
            //allowCoreThreadTimeOut默认为false，通过allowCoreThreadTimeOut()方法可以设置成true
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;
             
            //如果工作线程数大于最大线程数，这种情况发生的原因就是在运行期间，修改maximumPoolSize的值
            //timeout初始默认时false，当设置keepAliveTime时候，非核心线程超时了，就会变成true，具体看下面的分析
            if ((wc > maximumPoolSize || (timed && timedOut))
                && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }
 
            try {
                //如果核心线程允许超时，那所有的线程在从阻塞队列获取任务的时候都要加上超时时间，因为非核心线程默认是允许超时的
                //如果核心线程不允许超时，就直接调用take方法
                Runnable r = timed ?
                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                    workQueue.take();
                if (r != null)
                    return r;
                //到这里说明已经超时了
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }
```

该方法会控制while循环是否退出，当while循环退出的时候，就会销毁该线程，下面就总结一下该方法返回null的几种场景

- 线程池处于SHUTDOWN状态，且阻塞队列为空
- 线程池处于STOP,TIDYING,TERMINATED
- 工作线程数量大于最大线程数量
- 线程从阻塞队列获取任务超时，且线程数量大于1或者队列为空

这里解释下最后一条，从队列中获取任务超时，并且线程池中工作线程数大于1，也就是说最少为2，除了自己本身以外，还存在另一个线程，这个时候就可以直接销毁该线程，如果线程池就只有自己一个线程了，但是队列这个时候是空的，这时也可以把该线程销毁掉，这个时候线程池中就没有线程了。

上面已经提到processWorkerExit方法是销毁线程，下面就分析一下这个方法。

## 八、processWorkerExit(Worker w, boolean completedAbruptly)方法

```
private void processWorkerExit(Worker w, boolean completedAbruptly) {
        //如果该值为true，表示while循环执行时存在异常，并且没有捕获到，直接结束了
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();
 
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //将该线程完成的任务数汇总到总任务数上
            completedTaskCount += w.completedTasks;
            //从set集合中移除
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }
        // 根据线程池状态进行判断是否结束线程池
        tryTerminate();
 
        int c = ctl.get();
        //如果线程池状态处在RUNNING或者SHUTDOWN
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    //如果队列不为空，要保证线程池最少有一个线程
                    min = 1;
                //再次判断，如果工作线程大于min，直接返回，表示不需要新增线程，并且当前线程销毁成功
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            //如果阻塞队列不为空，并且线程池处于RUNNING或者SHUTDOWN状态，在不允许核心线程超时的情况下，要维持线程池中线程数是核心线程数
            addWorker(null, false);
        }
    }
```

从上面的代码可知，如下两点：

- 核心线程可以超时的情况下，只要阻塞队列不为空，要保证线程池中最少有一个线程，如果队列为空，是可以允许线程池中没有工作线程的。
- 如果核心线程不允许超时，则线程池中线程数要维持在和核心线程数一样，不管阻塞队列中有没有任务。

在该方法中调用了tryTerminate，该方法会尝试结束线程池，可能有的胖友会疑惑，为什么销毁一个线程的时候要尝试把整个线程池都给关了，这里就要看销毁线程的原因了，如果是因为线程池本身处于STOP等状态而销毁的线程，那这里就要关闭线程池了，具体可以看上面线程状态转换图。

## 九、tryTerminate()方法

```
final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            //如果处于RUNNING状态
            //或者大于等于TIDYING状态
            //或者处于SHUTDOWN但是队列不为空
            //以上3中情况直接返回，不会关闭线程池
            if (isRunning(c) ||
                runStateAtLeast(c, TIDYING) ||
                (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;
            if (workerCountOf(c) != 0) { // Eligible to terminate
                //中断空闲的线程，仅仅中断一个，至于为什么是一个，后面会分析
                interruptIdleWorkers(ONLY_ONE);
                return;
            }
 
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                //通过CAS将状态更新为TIDYING状态
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        //线程池中没有实现
                        terminated();
                    } finally {
                        //最终设置成TERMINATED状态，线程池彻底关闭
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }
```

这里看一下第二个if判断

```
if (workerCountOf(c) != 0) {
```

工作线程数不为零，运行到这行代码的时候，线程池状态有两个，第一就是处在STOP状态，第二就是处在SHUTDOWN状态，并且阻塞队列为空。下面具体分析一下这两种状态。

- 如果是STOP状态，执行下面的中断一个空闲线程没有意义，因为进入到STOP状态的时候就会中断所有空闲线程(空闲线程就是执行了workQueue.take的线程，但阻塞队列没有元素，一直等待)，之后其他线程是没有机会再次进入空闲状态的，因为线程池处于STOP状态，执行getTask的时候，会直接退出while循环，销毁该线程
- 如果是SHUTDOWN状态，在进入SHUTDOWN状态的时候，会中断空闲线程，但是非空闲线程没有中断，我们再来看一下getTask的代码

```
private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?
 
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);
 
            // Check if queue empty only if necessary.
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }
 
            int wc = workerCountOf(c);
 
            // Are workers subject to culling?
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;
 
            if ((wc > maximumPoolSize || (timed && timedOut))
                && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }
 
            try {
                Runnable r = timed ?
                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                    workQueue.take();
                if (r != null)
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }
```

在代码的第一个if判断中，如果线程池处于SHUTDOWN状态并且阻塞队列中有值就会继续执行，而不会执行return，但是执行到从队列中获取值的时候这时队列刚好为空了，这时该线程就会阻塞，所以上面中断一个空闲线程就可以把这样的处于等待的线程中断掉，虽然一次只中断了一个线程，每个被中断的空闲线程销毁的时候都会再中断下一个，这样会传递下去 ，不过仔细想想，觉得全部中断也可以。

上面介绍了线程池处在SHUTDOWN状态和STOP状态，下面就看一下线程池如何进入这两种状态的。

## 十、shutdown()方法

执行该方法会使线程池变成SHUTDOWN状态。

```
public void shutdown() {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        checkShutdownAccess();
        //修改线程池状态
        advanceRunState(SHUTDOWN);
        //中断空闲线程
        interruptIdleWorkers();
        onShutdown(); // hook for ScheduledThreadPoolExecutor
    } finally {
        mainLock.unlock();
    }
    //这里也执行了tryTerminate()，原因就是上面介绍的原因
    tryTerminate();
}
```

　　进入#advanceRunState()方法

```
private void advanceRunState(int targetState) {
    //死循环更新线程池状态为SHUTDOWN
    for (;;) {
        int c = ctl.get();
        if (runStateAtLeast(c, targetState) ||
            ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
            break;
    }
}
```

　　进入#interruptIdleWorkers()方法

```
private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }
 
private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                //先获取worker的锁，获取之后才可以中断，这也是在介绍Worker时候，提到Worker初始化的时候设置state为-1，为-1这里获取不到锁，无法中断
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }　　
```

## 十一、shutdownNow()方法

该方法会使线程池进入STOP状态，下面看一下其代码

```
public List<Runnable> shutdownNow() {
    List<Runnable> tasks;
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        checkShutdownAccess();
        //修改状态为STOP
        advanceRunState(STOP);
        //中断线程
        interruptWorkers();
        //清空阻塞队列
        tasks = drainQueue();
    } finally {
        mainLock.unlock();
    }
    tryTerminate();
    return tasks;
}
```

在最开始介绍线程所处的状态的时候，提到当线程池处于STOP状态的时候不在执行阻塞队列中的任务，实现这点的地方有三个地方

- 第一就是当线程池处于STOP状态，不在接受新任务
- 第二个就是getTask，如果处于STOP状态，就不在获取任务
- 第三个就是这里会把阻塞队列中的任务给干掉

 下面分析一下这个方法中调用的方法。

进入#interruptWorkers方法

```
private void interruptWorkers() {
     final ReentrantLock mainLock = this.mainLock;
     mainLock.lock();
     try {
         for (Worker w : workers)
             w.interruptIfStarted();
     } finally {
         mainLock.unlock();
     }
 }
 
     void interruptIfStarted() {
         Thread t;
         //只要state>=0且没有中断就会执行中断，无论当前worker是否处于加锁状态，都强制中断
         if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
             try {
                 t.interrupt();
             } catch (SecurityException ignore) {
             }
         }
     }
 }
```

可以看出该方法和shutdown()方法调用的interruptIdleWorkers()方法不同，该方法不会管是否加锁，只要state>=0就会执行中断操作，相当于把set集合中的worker全部中断，而interruptIdleWorkers只中断空闲的线程。

 

进入#drainQueue()方法

```
private List<Runnable> drainQueue() {
    BlockingQueue<Runnable> q = workQueue;
    ArrayList<Runnable> taskList = new ArrayList<Runnable>();
    //该方法会把队列中的元素都取出来放入到taskList中，但是可能会失败，所以下面做了判断，通过for循环移除，看一下官方的解释
    /**
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     */
    q.drainTo(taskList);
    if (!q.isEmpty()) {
        for (Runnable r : q.toArray(new Runnable[0])) {
            if (q.remove(r))
                taskList.add(r);
        }
    }
    return taskList;
}
```

## 十二、线程池的监控

通过线程池提供的参数进行监控。线程池里有一些属性在监控线程池的时候可以使用

- **getTaskCount**：线程池已经执行的和未执行的任务总数；
- **getCompletedTaskCount**：线程池已完成的任务数量，该值小于等于taskCount；
- **getLargestPoolSize**：线程池曾经创建过的最大线程数量。通过这个数据可以知道线程池是否满过，也就是达到了maximumPoolSize；
- **getPoolSize**：线程池当前的线程数量；
- **getActiveCount**：当前线程池中正在执行任务的线程数量。

通过这些方法，可以对线程池进行监控，在ThreadPoolExecutor类中提供了几个空方法，如beforeExecute方法，afterExecute方法和terminated方法，可以扩展这些方法在执行前或执行后增加一些新的操作，例如统计线程池的执行任务的时间等，可以继承自ThreadPoolExecutor来进行扩展。

## 十三、总结

本文详细介绍了线程池的创建、运行、关闭的过程。

- 线程池创建过程，详细介绍了每个参数的细节及扩展方法
- 提交任务到线程池，通过execute作为入口，介绍了任务提交到线程池之后，线程池根据当前运行线程数量进行的一系列动作
- 执行任务，分析了getTask从阻塞队列获取任务，以及根据线程池不同状态，做进一步处理
- 关闭线程池，介绍了通过修改线程池状态和中断线程的方式来结束线程池

　　线程池的使用难点在于如何确定合适的线程数量，如果设置过少，可能会导致大量任务堆积，如果设置过多，可能会导致CPU负载过高和线程上下文频繁切换导致的性能损耗严重。

## 十四、参考

[线程池原理分析(二)-ThreadPoolExecutor](https://www.cnblogs.com/gunduzi/p/13675708.html)



## END

