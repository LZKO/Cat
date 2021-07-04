# synchronized介绍



## 一、概述

synchronized是面试最高频的问题，比较简单的问题就是synchronized用在静态方法和非静态方法上的区别，复杂的问题就牵涉到synchronized如何保证原子性、有序性、可见性，以及synchronized锁优化和锁升级过程等，本文就介绍一下以上问题的原理。本文不涉及源码，如果有想要深入到源码级别的朋友，请看[这篇文章](https://blog.csdn.net/u011121287/article/details/106217887?utm_medium=distribute.pc_aggpage_search_result.none-task-blog-2~all~first_rank_v2~rank_v25-1-106217887.nonecase&utm_term=objectmonitor 内存地址)。

## 二、对象结构

本来要介绍锁，为什么要先在这里介绍对象结构呢？因为锁只能锁对象，类锁其实也是一种对象锁，因为所有的实例对象都对应同一个类的Class对象，每个类有一个唯一的Class对象，那类锁其实就是锁的Class对象，所以类锁就是全局锁，这里为什么要强调对象呢？因为对象才有对象头信息，而对象头中放着锁相关的信息，OK，下面就贴一下对象结构的结构信息，其包括对象头。

　　　　![img](C:\lyy\project_workspace\Cat\src\main\resources\pic\1407685-20200901233428679-177819255.png)

 

 　　图片来源：[Java并发基石——所谓“阻塞”：Object Monitor和AQS（1）](https://blog.csdn.net/yinwenjie/article/details/84922958)

下面就对上面三个区域进行逐一说明：

- 对齐区（Padding）：这个区域的主要作用就是补全用的，因为HotSpot JVM规定对象头的大小必须是8字节的整数倍，其实不是必须存在的，如果对象头刚刚好就是8字节的整数倍就不需要了。
- 对象数据：这个区域是真实对象的信息，包括所有的字段属性信息，他们可能是其他对象的引用或者实际的数据值。
- 对象头（Header）：对象头是重点关注的地方，下面会专门介绍

## 三、对象头

以32为Java虚拟机为例介绍。

普通对象的对象头

```
|--------------------------------------------------------------|
|           Object Header (``64` `bits)         |
|------------------------------------|-------------------------|
|    Mark Word (``32` `bits)     |  Klass Word (``32` `bits) |
|------------------------------------|-------------------------|
```

数组对象的对象头

```
|---------------------------------------------------------------------------------|
|                 Object Header (``96` `bits)             |
|--------------------------------|-----------------------|------------------------|
|    Mark Word(32bits)    |  Klass Word(32bits) | array length(32bits) |
|--------------------------------|-----------------------|------------------------|
```

下面就对每个区域做一个说明：

上图中第一行Object Header，就是这次要介绍的对象头了，对于非数组对象来说，对象头中就只包含两个信息：

- Klass：这是一个指针区域，对于Java1.8来说，就是指向元空间，这里面存放的是被加载的.class文件信息，但是，不是初始化之后的Class对象，初始化之后的Class对象放在堆中。
- Mark Word：终于介绍到今天的主角了，保存对象运行时相关数据，比如hashCode值、gc分代年龄、锁等信息，里面的内容不是固定的，随着对象的运行，里面的内容会发生变化。

## 四、Mark Word

Mark Word在不同的锁状态下存储的内容不同，32虚拟机是如下方式存储的。

| 锁状态   | 25bit                | 4bit       | 1bit     | 2bit |      |
| -------- | -------------------- | ---------- | -------- | ---- | ---- |
| 23bit    | 2bit                 | 是否偏向锁 | 锁标志位 |      |      |
| 无锁     | 对象的HashCode       | 分代年龄   | 0        | 01   |      |
| 偏向锁   | 线程ID               | Epoch      | 分代年龄 | 1    | 01   |
| 轻量级锁 | 指向栈中锁记录的指针 | 00         |          |      |      |
| 重量级锁 | 指向重量级锁的指针   | 10         |          |      |      |
| GC标记   | 空                   | 11         |          |      |      |

**注意**：上面表格中的写法，可能会引起误解，认为以上所有的信息都存在Mark Word中，其实不是，其实对象的锁状态只能处于其中一种，也就是说上面无锁、偏向锁、轻量级锁、重量级锁这几行其实每次只能一行存在，那对象头为什么设计成可变的呢？主要是为了节省空间，因为Java中一切皆对象，如果对象头占用了过多的空间，那所有对象的对象头累加起来占用的空间就很可观了。

下面就介绍一下synchronized的锁升级过程，从无锁 -> 偏向锁 -> 轻量级锁 -> 重量级锁，以及在锁升级的过程中对象头的变化。

## 五、无锁

没有对资源进行锁定，所有的线程都能访问并修改同一个资源，但同时只有一个线程能修改成功，其他修改失败的线程会不断重试直到修改成功。无锁状态对象头锁状态如下

| 无锁 | 对象的HashCode | 分代年龄 | 0    | 01   |
| ---- | -------------- | -------- | ---- | ---- |
|      |                |          |      |      |

## 六、偏向锁

HotSpot的作者经过研究发现，大多数情况下，锁不仅不存在多线程竞争，而且总是由同一线程多次获得，为了让线程获得锁的代价更低而引入了偏向锁。

#### 偏向锁的获取

当一个线程（线程A）访问同步块并获取锁时，会在**对象头**和**栈帧中的锁记录**里存储锁偏向的线程ID，以后该线程在进入和退出同步块时不需要进行CAS操作来加锁和解锁，只需简单地测试一下对象头的Mark Word里是否存储着指向当前线程的偏向锁。如果测试成功，表示线程已经获得了锁。如果测试失败，则需要再测试一下Mark Word中偏向锁的标识是否设置成1（表示当前是偏向锁），并且锁状态是否是01(表示无锁状态)，如果没有设置，则使用CAS竞争锁；如果设置了，则尝试使用CAS将对象头的偏向锁指向当前线程

| 偏向锁 | 线程A ID | Epoch | 分代年龄 | 1    | 01   |
| ------ | -------- | ----- | -------- | ---- | ---- |
|        |          |       |          |      |      |



 流程图如下：

​    ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200902110046875-1931664253.png)

　　　　　　图片来源：[jvm：ObjectMonitor源码](https://blog.csdn.net/u011121287/article/details/106217887?utm_medium=distribute.pc_aggpage_search_result.none-task-blog-2~all~first_rank_v2~rank_v25-1-106217887.nonecase&utm_term=objectmonitor 内存地址)

 以上流程图和上面的说法稍微有些出入，大家自己斟酌，不过这里还有一个疑问，首次将偏向锁标识修改成1是怎么进行的，等有时间好好研究下源码吧。

#### 偏向锁撤销

上面如果线程B通过CAS加锁失败，就表明当前环境中存在锁竞争，当然这个时候并不会直接升级为轻量级锁，而是先把拥有偏向锁的线程的偏向锁给撤销了。有下面两种情况

1. 如果拥有偏向锁的线程不处于活动状态或者已经退出同步代码块，这时把对象设置为无锁状态，然后重新偏向
2. 如果拥有偏向锁的线程处于活动状态，而且依然需要使用偏向锁，则升级为轻量级锁

偏向锁的撤销并不是主动的，就是说拥有偏向锁的线程在执行完同步代码块的时候，并不会主动退出偏向锁，而是需要另一个线程来竞争偏向锁的时候，才会撤销，而且还需要程序运行到全局安全点（这个在GC垃圾回收时也有，我想和那个是一样的，在垃圾回收的时候，只有当线程运行到safepoint才会暂停等待GC的结束，否则就会一直运行），之后暂停线程，检查线程的状态是否处于活动状态，接下来就是上面介绍的那两点了。

流程图如下：

​    ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200902110447439-1841816338.png)

​                图片来源：[jvm：ObjectMonitor源码](https://blog.csdn.net/u011121287/article/details/106217887?utm_medium=distribute.pc_aggpage_search_result.none-task-blog-2~all~first_rank_v2~rank_v25-1-106217887.nonecase&utm_term=objectmonitor 内存地址)

## 七、轻量级锁

引入轻量级锁的主要目的是在没有多线程竞争的前提下，减少传统的重量级锁使用操作系统互斥量产生的性能消耗。

#### 轻量级锁获取

当关闭偏向锁功能或者多个线程竞争偏向锁导致偏向锁升级为轻量级锁，则会尝试获取轻量级锁，过程如下：

![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200902093023025-1935257906.png)

1. 判断当前对象是否处于无锁状态（hashcode、0、01），若是，则JVM首先将在当前线程的栈帧中建立一个名为锁记录（Lock Record）的空间，用于存储锁对象目前的Mark Word的拷贝（官方把这份拷贝加了一个Displaced前缀，即Displaced Mark Word），就是图中的1；否则执行步骤（3）；
2. JVM利用CAS操作尝试将对象的Mark Word更新为指向Lock Record的指正，就是图中的2，如果成功表示竞争到锁，则将锁标志位变成00（表示此对象处于轻量级锁状态），加锁成功之后，会执行图中的3，owner指向对象的mark word，之后执行同步操作；如果失败则执行步骤（3）；
3. 判断当前对象的Mark Word是否指向当前线程的栈帧，如果是则表示当前线程已经持有当前对象的锁，则直接执行同步代码块；否则只能说明该锁对象已经被其他线程抢占了，这时轻量级锁需要膨胀为重量级锁，锁标志位变成10，后面等待的线程将会进入阻塞状态；

#### 轻量级锁释放

过程如下：

1. 取出在获取轻量级锁保存在Displaced Mark Word中的数据；
2. 用CAS操作将取出的数据替换当前对象的Mark Word中，如果成功，则说明释放锁成功，否则执行（3）；
3. 如果CAS操作替换失败，说明有其他线程尝试获取该锁，则需要在释放锁的同时需要唤醒被挂起的线程。

释放锁的过程非常简单，就是把栈帧中的Mark Word通过CAS给更新到对象的Mark Word中，问题是这个过程为什么会失败，因为此时只有这个线程获取到轻量级锁，释放的时候就只有一个线程，没有别的线程和他竞争，他的CAS操作为什么会失败呢？

原因如下：比如线程A获取到轻量级锁，如果这个时候线程B进来竞争锁，没有竞争到，然后自旋，自旋到一定时候，还没有获取到，膨胀为重量级锁，此时线程Mark Word已经发生了变更，在变更之前Mark Word的锁区域如下：

| 轻量级锁 | 指向栈中锁记录的指针 | 00   |
| -------- | -------------------- | ---- |
|          |                      |      |

但是由于另一个线程已经膨胀为重量级锁，新建了ObjectMonitor(这个之后会介绍），这个时候Mark Word锁记录如下：

| 重量级锁 | 指向重量级锁的指针 | 10   |
| -------- | ------------------ | ---- |
|          |                    |      |

此时的指针已经指向了ObjectMonitor,而不再是线程A的栈帧，所以当线程A通过CAS进行修改对象的Mark Word的时候会失败，此时线程A自旋等待锁膨胀结束，执行退出重量级锁。

流程图如下：

下图中所说的图A在上面。

​    ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200902110722360-116051040.png)

 　　　　　　　　　　　　　图片来源：[jvm：ObjectMonitor源码](https://blog.csdn.net/u011121287/article/details/106217887?utm_medium=distribute.pc_aggpage_search_result.none-task-blog-2~all~first_rank_v2~rank_v25-1-106217887.nonecase&utm_term=objectmonitor 内存地址)

## 八、重量级锁

重量级锁通过对象内部的监视器（monitor）实现，其中monitor的本质是依赖于底层操作系统的Mutex Lock实现，操作系统实现线程之间的切换需要从用户态到内核态的切换，切换成本非常高。下面介绍一下重量级锁的加锁过程。

​    ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200902100207644-1699149539.png)

解释：

- 最左边是要竞争重量级锁的线程，这些个线程还没有竞争到锁的呢，就放在对象Monitor的entrylist中，线程的状态为Blocked
- entrylist中的线程通过CAS来竞争锁，就是通过CAS来修改count的值，如果成功就获取到锁，同时由于synchronized是可重入锁，每次重入，只要将count进行加1操作即可
- 如果线程1在进入同步代码块之后，执行了wait操作，把自己给挂起了，就要把线程1放入到waitset中，然后重新执行第二步
- 处于waitset集合中的线程需要别的线程执行notify/notifyall等操作才可以被唤醒继续执行。

## 九、各种锁对比

​    ![img](https://img2020.cnblogs.com/blog/1407685/202009/1407685-20200902101101219-218883198.png)

## 十、 锁优化

**自旋锁**：自旋锁就是如果线程没有获取到锁，就让线程空跑一段时间，为什么线程没有获取到锁，不直接挂起呢？因为线程挂起要进行上下文切换，对于CPU来说是一个很繁重的工作，所以就让线程空跑一下，但是空跑同样会浪费CPU，所以要加一个限制，限制空跑的时间，如果在限制时间内没有获取到锁就挂起。自旋锁在JDK 1.4.2中引入，默认关闭，但是可以使用-XX:+UseSpinning开开启，在JDK1.6中默认开启。同时自旋的默认次数为10次，可以通过参数-XX:PreBlockSpin来调整； 如果通过参数-XX:preBlockSpin来调整自旋锁的自旋次数，会带来诸多不便。假如我将参数调整为10，但是系统很多线程都是等你刚刚退出的时候就释放了锁（假如你多自旋一两次就可以获取锁），你是不是很尴尬。于是JDK1.6引入自适应的自旋锁，让虚拟机会变得越来越聪明。

**适应自旋锁**：JDK 1.6引入了更加聪明的自旋锁，即自适应自旋锁。所谓自适应就意味着自旋的次数不再是固定的，它是由前一次在同一个锁上的自旋时间及锁的拥有者的状态来决定。它怎么做呢？线程如果自旋成功了，那么下次自旋的次数会更加多，因为虚拟机认为既然上次成功了，那么此次自旋也很有可能会再次成功，那么它就会允许自旋等待持续的次数更多。反之，如果对于某个锁，很少有自旋能够成功的，那么在以后要或者这个锁的时候自旋的次数会减少甚至省略掉自旋过程，以免浪费处理器资源。 有了自适应自旋锁，随着程序运行和性能监控信息的不断完善，虚拟机对程序锁的状况预测会越来越准确，虚拟机会变得越来越聪明。

**锁消除**：就是有些场景下，我们加了锁，但是JVM通过分析发现共享数据不存在竞争，这个时候JVM就会进行锁消除。

**锁粗化**：就是将多个加锁，解锁连接到一起，扩展成一个范围更大的锁。如下例子：

```
public void vectorTest(){
    Vector<String> vector = new Vector<String>();
    for(int i = 0 ; i < 10 ; i++){
        vector.add(i + "");
    }
 
    System.out.println(vector);
}
```

vector每次执行add方法都要进行加锁解锁的操作，效率非常低下，JVM会检测到对同一个对象vector连续加锁解锁，会合并成一个更大的加锁解锁，就是把锁移动到for之外。

## 十一、synchronized如何保证可见性和有序性

上面介绍了那么一大堆，其实都是在介绍synchronized如何保证原子性，那可见性和有序性如何保证呢，其实synchronized保证可见性和有序性的方法和volatile类似，如下图：

​    ![img](C:\lyy\project_workspace\Cat\src\main\notes\interview\pic\1407685-20200902113208311-1653247671.png)

上面内存屏障是什么意思，参考我的[另一篇volatile的文章](https://www.cnblogs.com/gunduzi/p/13596273.html)

## 十三、总结

本文主要介绍了synchronized如何保证原子性、可见性、有序性，在介绍原子性的时候介绍了synchronized非常多的手段用来保证原子性，其实上面搞了一大堆锁优化，锁升级，就是为了提高效率，我们使用多线程的目的其实也是为了提高效率，那锁的作用是什么，为了保证多线程的安全，但是保证了安全，效率没了，那和单线程有什么区别，所以才会在锁上面做了那么多的优化。

## 十四、附录：Synchronzied的底层原理

参考：[jvm：ObjectMonitor源码](https://blog.csdn.net/zwjyyy1203/article/details/106217887?utm_medium=distribute.pc_aggpage_search_result.none-task-blog-2~all~first_rank_v2~rank_v25-1-106217887.nonecase&utm_term=objectmonitor%20%E5%86%85%E5%AD%98%E5%9C%B0%E5%9D%80)

对象头和内置锁(ObjectMonitor)

根据jvm的分区，对象分配在堆内存中，可以用下图表示：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzMS5wbmc?x-oss-process=image/format,png)

对象头

Hotspot虚拟机的对象头包括两部分信息，第一部分用于储存对象自身的运行时数据，如哈希码，GC分代年龄，锁状态标志，锁指针等，这部分数据在32bit和64bit的虚拟机中大小分别为32bit和64bit，官方称它为"Mark word",考虑到虚拟机的空间效率，Mark Word被设计成一个非固定的数据结构以便在极小的空间中存储尽量多的信息，它会根据对象的状态复用自己的存储空间，详细情况如下图：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzMTEucG5n?x-oss-process=image/format,png)

对象头的另外一部分是类型指针，即对象指向它的类元数据的指针，如果对象访问定位方式是句柄访问，那么该部分没有，如果是直接访问，该部分保留。句柄访问方式如下图：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzMi5wbmc?x-oss-process=image/format,png)

直接访问如下图：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzMy5wbmc?x-oss-process=image/format,png)

内置锁(ObjectMonitor)

通常所说的对象的内置锁，是对象头Mark Word中的重量级锁指针指向的monitor对象，该对象是在HotSpot底层C++语言编写的(openjdk里面看)，简单看一下代码：

```
//结构体如下
ObjectMonitor::ObjectMonitor() {  
  _header       = NULL;  
  _count       = 0;  
  _waiters      = 0,  
  _recursions   = 0;       //线程的重入次数
  _object       = NULL;  
  _owner        = NULL;    //标识拥有该monitor的线程
  _WaitSet      = NULL;    //等待线程组成的双向循环链表，_WaitSet是第一个节点
  _WaitSetLock  = 0 ;  
  _Responsible  = NULL ;  
  _succ         = NULL ;  
  _cxq          = NULL ;    //多线程竞争锁进入时的单向链表
  FreeNext      = NULL ;  
  _EntryList    = NULL ;    //_owner从该双向循环链表中唤���线程结点，_EntryList是第一个节点
  _SpinFreq     = 0 ;  
  _SpinClock    = 0 ;  
  OwnerIsThread = 0 ;  
}  
```

ObjectMonitor队列之间的关系转换可以用下图表示：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzNC5wbmc?x-oss-process=image/format,png)

既然提到了_waitSet和_EntryList(_cxq队列后面会说)，那就看一下底层的wait和notify方法
wait方法的实现过程:

ait方法的实现过程:

```
  //1.调用ObjectSynchronizer::wait方法
void ObjectSynchronizer::wait(Handle obj, jlong millis, TRAPS) {
  /*省略 */
  //2.获得Object的monitor对象(即内置锁)
  ObjectMonitor* monitor = ObjectSynchronizer::inflate(THREAD, obj());
  DTRACE_MONITOR_WAIT_PROBE(monitor, obj(), THREAD, millis);
  //3.调用monitor的wait方法
  monitor->wait(millis, true, THREAD);
  /*省略*/
}
  //4.在wait方法中调用addWaiter方法
  inline void ObjectMonitor::AddWaiter(ObjectWaiter* node) {
  /*省略*/
  if (_WaitSet == NULL) {
    //_WaitSet为null，就初始化_waitSet
    _WaitSet = node;
    node->_prev = node;
    node->_next = node;
  } else {
    //否则就尾插
    ObjectWaiter* head = _WaitSet ;
    ObjectWaiter* tail = head->_prev;
    assert(tail->_next == head, "invariant check");
    tail->_next = node;
    head->_prev = node;
    node->_next = head;
    node->_prev = tail;
  }
}
  //5.然后在ObjectMonitor::exit释放锁，接着 thread_ParkEvent->park  也就是wait
```

总结：通过object获得内置锁(objectMonitor)，通过内置锁将Thread封装成OjectWaiter对象，然后addWaiter将它插入以_waitSet为首结点的等待线程链表中去，最后释放锁。

notify方法的底层实现

```
    //1.调用ObjectSynchronizer::notify方法



    void ObjectSynchronizer::notify(Handle obj, TRAPS) {



    /*省略*/



    //2.调用ObjectSynchronizer::inflate方法



    ObjectSynchronizer::inflate(THREAD, obj())->notify(THREAD);



}



    //3.通过inflate方法得到ObjectMonitor对象



    ObjectMonitor * ATTR ObjectSynchronizer::inflate (Thread * Self, oop object) {



    /*省略*/



     if (mark->has_monitor()) {



          ObjectMonitor * inf = mark->monitor() ;



          assert (inf->header()->is_neutral(), "invariant");



          assert (inf->object() == object, "invariant") ;



          assert (ObjectSynchronizer::verify_objmon_isinpool(inf), "monitor is inva;lid");



          return inf 



      }



    /*省略*/ 



      }



    //4.调用ObjectMonitor的notify方法



    void ObjectMonitor::notify(TRAPS) {



    /*省略*/



    //5.调用DequeueWaiter方法移出_waiterSet第一个结点



    ObjectWaiter * iterator = DequeueWaiter() ;



    //6.后面省略是将上面DequeueWaiter尾插入_EntrySet的操作



    /**省略*/



  }
```

总结：通过object获得内置锁(objectMonitor)，调用内置锁的notify方法，通过_waitset结点移出等待链表中的首结点，将它置于_EntrySet中去，等待获取锁。注意：notifyAll根据policy不同可能移入_EntryList或者_cxq队列中，此处不详谈。

在了解对象头和ObjectMonitor后，接下来我们结合分析synchronzied的底层实现。

synchronzied的底层原理

synchronized修饰代码块

通过下列简介的代码来分析：

```
public class test{



    public void testSyn(){



        synchronized(this){



        }



    }



}
```

javac编译，javap -verbose反编译，结果如下：

```
 /**



 * ...



 **/



  public void testSyn();



    descriptor: ()V



    flags: ACC_PUBLIC



    Code:



      stack=2, locals=3, args_size=1



         0: aload_0              



         1: dup                 



         2: astore_1            



         3: monitorenter        //申请获得对象的内置锁



         4: aload_1             



         5: monitorexit         //释放对象内置锁



         6: goto          14



         9: astore_2



        10: aload_1



        11: monitorexit         //释放对象内置锁



        12: aload_2



        13: athrow



        14: return
```

此处我们只讨论了重量级锁(ObjectMonitor)的获取情况，其他锁的获取放在后面synchronzied的优化中进行说明。源码如下：

```
void ATTR ObjectMonitor::enter(TRAPS) {
  Thread * const Self = THREAD ;
  void * cur ;
  //通过CAS操作尝试把monitor的_owner字段设置为当前线程
  cur = Atomic::cmpxchg_ptr (Self, &_owner, NULL) ;
  //获取锁失败
  if (cur == NULL) {
     assert (_recursions == 0   , "invariant") ;
     assert (_owner      == Self, "invariant") ;
     return ;
  }
//如果之前的_owner指向该THREAD，那么该线程是重入，_recursions++
  if (cur == Self) {
     _recursions ++ ;
     return ;
  }
//如果当前线程是第一次进入该monitor，设置_recursions为1，_owner为当前线程
  if (Self->is_lock_owned ((address)cur)) {
    assert (_recursions == 0, "internal state error");
    _recursions = 1 ;   //_recursions标记为1
    _owner = Self ;     //设置owner
    OwnerIsThread = 1 ;
    return ;
  }
  /**
  *此处省略锁的自旋优化等操作，统一放在后面synchronzied优化中说
  **/
```

总结：

1. 如果monitor的进入数为0，则该线程进入monitor，然后将进入数设置为1，该线程即为monitor的owner
2. 如果线程已经占有该monitor，只是重新进入，则进入monitor的进入数加1.
3. 如果其他线程已经占用了monitor，则该线程进入阻塞状态，直到monitor的进入数为0，再重新尝试获取monitor的所有权

synchronized修饰方法

还是从简洁的代码来分析：

```
public class test{



    public synchronized  void testSyn(){



    }



}
```

javac编译，javap -verbose反编译，结果如下：

```
 /**



 * ...



 **/



  public synchronized void testSyn();



    descriptor: ()V



    flags: ACC_PUBLIC, ACC_SYNCHRONIZED



    Code:



      stack=0, locals=1, args_size=1



         0: return



      LineNumberTable:



        line 3: 0
```

结果和synchronized修饰代码块的情况不同，仔细比较会发现多了ACC_SYNCHRONIZED这个标识，test.java通过javac编译形成的test.class文件，在该文件中包含了testSyn方法的方法表，其中ACC_SYNCHRONIZED标志位是1，当线程执行方法的时候会检查该标志位，如果为1，就自动的在该方法前后添加monitorenter和monitorexit指令，可以称为monitor指令的隐式调用。

上面所介绍的通过synchronzied实现同步用到了对象的内置锁(ObjectMonitor)，而在ObjectMonitor的函数调用中会涉及到Mutex lock等特权指令，那么这个时候就存在操作系统用户态和核心态的转换，这种切换会消耗大量的系统资源，因为用户态与内核态都有各自专用的内存空间，专用的寄存器等，用户态切换至内核态需要传递给许多变量、参数给内核，内核也需要保护好用户态在切换时的一些寄存器值、变量等，这也是为什么早期的synchronized效率低的原因。在jdk1.6之后，从jvm层面做了很大的优化，下面主要介绍做了哪些优化。

**synchronized的优化**

在了解了synchronized重量级锁效率特别低之后，jdk自然做了一些优化，出现了偏向锁，轻量级锁，重量级锁，自旋等优化，我们应该改正monitorenter指令就是获取对象重量级锁的错误认识，很显然，优化之后，锁的获取判断次序是偏向锁->轻量级锁->重量级锁。

偏向锁

源码如下：

```
//偏向锁入口



void ObjectSynchronizer::fast_enter(Handle obj, BasicLock* lock, bool attempt_rebias, TRAPS) {



 //UseBiasedLocking判断是否开启偏向锁



 if (UseBiasedLocking) {



    if (!SafepointSynchronize::is_at_safepoint()) {



      //获取偏向锁的函数调用



      BiasedLocking::Condition cond = BiasedLocking::revoke_and_rebias(obj, attempt_rebias, THREAD);



      if (cond == BiasedLocking::BIAS_REVOKED_AND_REBIASED) {



        return;



      }



    } else {



      assert(!attempt_rebias, "can not rebias toward VM thread");



      BiasedLocking::revoke_at_safepoint(obj);



    }



 }



 //不能偏向，就获取轻量级锁



 slow_enter (obj, lock, THREAD) ;



}
```

BiasedLocking::revoke_and_rebias调用过程如下流程图：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzMTIucG5n?x-oss-process=image/format,png)

偏向锁的撤销过程如下：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzNS5wbmc?x-oss-process=image/format,png)

 

轻量级锁

轻量级锁获取源码：

```
//轻量级锁入口



void ObjectSynchronizer::slow_enter(Handle obj, BasicLock* lock, TRAPS) {



  markOop mark = obj->mark();  //获得Mark Word



  assert(!mark->has_bias_pattern(), "should not see bias pattern here");



  //是否无锁不可偏向，标志001



  if (mark->is_neutral()) {



    //图A步骤1



    lock->set_displaced_header(mark);



    //图A步骤2



    if (mark == (markOop) Atomic::cmpxchg_ptr(lock, obj()->mark_addr(), mark)) {



      TEVENT (slow_enter: release stacklock) ;



      return ;



    }



    // Fall through to inflate() ...



  } else if (mark->has_locker() && THREAD->is_lock_owned((address)mark->locker())) { //如果Mark Word指向本地栈帧，线程重入



    assert(lock != mark->locker(), "must not re-lock the same lock");



    assert(lock != (BasicLock*)obj->mark(), "don't relock with same BasicLock");



    lock->set_displaced_header(NULL);//header设置为null



    return;



  }



  lock->set_displace



 



  d_header(markOopDesc::unused_mark());



  //轻量级锁膨胀，膨胀完成之后尝试获取重量级锁



  ObjectSynchronizer::inflate(THREAD, obj())->enter(THREAD);



}
```

轻量级锁获取流程如下：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzMTMucG5n?x-oss-process=image/format,png)

![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzMTQucG5n?x-oss-process=image/format,png)

轻量级锁撤销源码：

```
void ObjectSynchronizer::fast_exit(oop object, BasicLock* lock, TRAPS) {



  assert(!object->mark()->has_bias_pattern(), "should not see bias pattern here");



  markOop dhw = lock->displaced_header();



  markOop mark ;



  if (dhw == NULL) {//如果header为null，说明这是线程重入的栈帧，直接返回，不用回写



     mark = object->mark() ;



     assert (!mark->is_neutral(), "invariant") ;



     if (mark->has_locker() && mark != markOopDesc::INFLATING()) {



        assert(THREAD->is_lock_owned((address)mark->locker()), "invariant") ;



     }



     if (mark->has_monitor()) {



        ObjectMonitor * m = mark->monitor() ;



     }



     return ;



  }



 



  mark = object->mark() ;



  if (mark == (markOop) lock) {



     assert (dhw->is_neutral(), "invariant") ;



     //CAS将Mark Word内容写回



     if ((markOop) Atomic::cmpxchg_ptr (dhw, object->mark_addr(), mark) == mark) {



        TEVENT (fast_exit: release stacklock) ;



        return;



     }



  }



  //CAS操作失败，轻量级锁膨胀，为什么在撤销锁的时候会有失败的可能？



   ObjectSynchronizer::inflate(THREAD, object)->exit (THREAD) ;



}
```

轻量级锁撤销流程如下：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzNi5wbmc?x-oss-process=image/format,png)

 

轻量级锁膨胀

源代码：

```
ObjectMonitor * ATTR ObjectSynchronizer::inflate (Thread * Self, oop object) {



  assert (Universe::verify_in_progress() ||



          !SafepointSynchronize::is_at_safepoint(), "invariant") ;



  for (;;) { // 为后面的continue操作提供自旋



      const markOop mark = object->mark() ; //获得Mark Word结构



      assert (!mark->has_bias_pattern(), "invariant") ;



 



      //Mark Word可能有以下几种状态:



      // *  Inflated(膨胀完成)     - just return



      // *  Stack-locked(轻量级锁) - coerce it to inflated



      // *  INFLATING(膨胀中)     - busy wait for conversion to complete



      // *  Neutral(无锁)        - aggressively inflate the object.



      // *  BIASED(偏向锁)       - Illegal.  We should never see this



 



      if (mark->has_monitor()) {//判断是否是重量级锁



          ObjectMonitor * inf = mark->monitor() ;



          assert (inf->header()->is_neutral(), "invariant");



          assert (inf->object() == object, "invariant") ;



          assert (ObjectSynchronizer::verify_objmon_isinpool(inf), "monitor is invalid");



          //Mark->has_monitor()为true，说明已经是重量级锁了，膨胀过程已经完成，返回



          return inf ;



      }



      if (mark == markOopDesc::INFLATING()) { //判断是否在膨胀



         TEVENT (Inflate: spin while INFLATING) ;



         ReadStableMark(object) ;



         continue ; //如果正在膨胀，自旋等待膨胀完成



      }



 



      if (mark->has_locker()) { //如果当前是轻量级锁



          ObjectMonitor * m = omAlloc (Self) ;//返回一个对象的内置ObjectMonitor对象



          m->Recycle();



          m->_Responsible  = NULL ;



          m->OwnerIsThread = 0 ;



          m->_recursions   = 0 ;



          m->_SpinDuration = ObjectMonitor::Knob_SpinLimit ;//设置自旋获取重量级锁的次数



          //CAS操作标识Mark Word正在膨胀



          markOop cmp = (markOop) Atomic::cmpxchg_ptr (markOopDesc::INFLATING(), object->mark_addr(), mark) ;



          if (cmp != mark) {



             omRelease (Self, m, true) ;



             continue ;   //如果上述CAS操作失败，自旋等待膨胀完成



          }



          m->set_header(dmw) ;



          m->set_owner(mark->locker());//设置ObjectMonitor的_owner为拥有对象轻量级锁的线程，而不是当前正在inflate的线程



          m->set_object(object);



          /**



          *省略了部分代码



          **/



          return m ;



      }



  }



}
```

轻量级锁膨胀流程图：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzNy5wbmc?x-oss-process=image/format,png)

现在来回答下之前提出的问题：为什么在撤销轻量级锁的时候会有失败的可能？
假设thread1拥有了轻量级锁，Mark Word指向thread1栈帧，thread2请求锁的时候，就会膨胀初始化ObjectMonitor对象，将Mark Word更新为指向ObjectMonitor的指针，那么在thread1退出的时候，CAS操作会失败，因为Mark Word不再指向thread1的栈帧，这个时候thread1自旋等待infalte完毕，执行重量级锁的退出操作

 

重量级锁

重量级锁的获取入口：

```
void ATTR ObjectMonitor::enter(TRAPS) {



  Thread * const Self = THREAD ;



  void * cur ;



  cur = Atomic::cmpxchg_ptr (Self, &_owner, NULL) ;



  if (cur == NULL) {



     assert (_recursions == 0   , "invariant") ;



     assert (_owner      == Self, "invariant") ;



     return ;



  }



 



  if (cur == Self) {



     _recursions ++ ;



     return ;



  }



 



  if (Self->is_lock_owned ((address)cur)) {



    assert (_recursions == 0, "internal state error");



    _recursions = 1 ;



    // Commute owner from a thread-specific on-stack BasicLockObject address to



    // a full-fledged "Thread *".



    _owner = Self ;



    OwnerIsThread = 1 ;



    return ;



  }



  /**



  *上述部分在前面已经分析过，不再累述



  **/



 



  Self->_Stalled = intptr_t(this) ;



  //TrySpin是一个自旋获取锁的操作，此处就不列出源码了



  if (Knob_SpinEarly && TrySpin (Self) > 0) {



     Self->_Stalled = 0 ;



     return ;



  }



  /*



  *省略部分代码



  */



    for (;;) {



      EnterI (THREAD) ;



      /**



      *省略了部分代码



      **/



  }



}
```

进入EnterI (TRAPS)方法(这段代码个人觉得很有意思):

```
void ATTR ObjectMonitor::EnterI (TRAPS) {



    Thread * Self = THREAD ;



    if (TryLock (Self) > 0) {



        //这下不自旋了，我就默默的TryLock一下



        return ;



    }



 



    DeferredInitialize () ;



    //此处又有自旋获取锁的操作



    if (TrySpin (Self) > 0) {



        return ;



    }



    /**



    *到此，自旋终于全失败了，要入队挂起了



    **/



    ObjectWaiter node(Self) ; //将Thread封装成ObjectWaiter结点



    Self->_ParkEvent->reset() ;



    node._prev   = (ObjectWaiter *) 0xBAD ; 



    node.TState  = ObjectWaiter::TS_CXQ ; 



    ObjectWaiter * nxt ;



    for (;;) { //循环，保证将node插入队列



        node._next = nxt = _cxq ;//将node插入到_cxq队列的首部



        //CAS修改_cxq指向node



        if (Atomic::cmpxchg_ptr (&node, &_cxq, nxt) == nxt) break ;



        if (TryLock (Self) > 0) {//我再默默的TryLock一下，真的是不想挂起呀！



            return ;



        }



    }



    if ((SyncFlags & 16) == 0 && nxt == NULL && _EntryList == NULL) {



        // Try to assume the role of responsible thread for the monitor.



        // CONSIDER:  ST vs CAS vs { if (Responsible==null) Responsible=Self }



        Atomic::cmpxchg_ptr (Self, &_Responsible, NULL) ;



    }



    TEVENT (Inflated enter - Contention) ;



    int nWakeups = 0 ;



    int RecheckInterval = 1 ;



 



    for (;;) {



        if (TryLock (Self) > 0) break ;//临死之前，我再TryLock下



 



        if ((SyncFlags & 2) && _Responsible == NULL) {



           Atomic::cmpxchg_ptr (Self, &_Responsible, NULL) ;



        }



        if (_Responsible == Self || (SyncFlags & 1)) {



            TEVENT (Inflated enter - park TIMED) ;



            Self->_ParkEvent->park ((jlong) RecheckInterval) ;



            RecheckInterval *= 8 ;



            if (RecheckInterval > 1000) RecheckInterval = 1000 ;



        } else {



            TEVENT (Inflated enter - park UNTIMED) ;



            Self->_ParkEvent->park() ; //终于挂起了



        }



 



        if (TryLock(Self) > 0) break ;



        /**



        *后面代码省略



        **/



}
```

try了那么多次lock，接下来看下TryLock:

```
int ObjectMonitor::TryLock (Thread * Self) {



   for (;;) {



      void * own = _owner ;



      if (own != NULL) return 0 ;//如果有线程还拥有着重量级锁，退出



      //CAS操作将_owner修改为当前线程，操作成功return>0



      if (Atomic::cmpxchg_ptr (Self, &_owner, NULL) == NULL) {



         return 1 ;



      }



      //CAS更新失败return<0



      if (true) return -1 ;



   }



}
```

重量级锁获取入口流程图：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzMTAucG5n?x-oss-process=image/format,png)

重量级锁的出口：

```
void ATTR ObjectMonitor::exit(TRAPS) {



   Thread * Self = THREAD ;



   if (THREAD != _owner) {



     if (THREAD->is_lock_owned((address) _owner)) {



       _owner = THREAD ;



       _recursions = 0 ;



       OwnerIsThread = 1 ;



     } else {



       TEVENT (Exit - Throw IMSX) ;



       if (false) {



          THROW(vmSymbols::java_lang_IllegalMonitorStateException());



       }



       return;



     }



   }



   if (_recursions != 0) {



     _recursions--;        // 如果_recursions次数不为0.自减



     TEVENT (Inflated exit - recursive) ;



     return ;



   }



   if ((SyncFlags & 4) == 0) {



      _Responsible = NULL ;



   }



 



   for (;;) {



      if (Knob_ExitPolicy == 0) {



         OrderAccess::release_store_ptr (&_owner, NULL) ;   // drop the lock



         OrderAccess::storeload() ;                         



         if ((intptr_t(_EntryList)|intptr_t(_cxq)) == 0 || _succ != NULL) {



            TEVENT (Inflated exit - simple egress) ;



            return ;



         }



         TEVENT (Inflated exit - complex egress) ;



         if (Atomic::cmpxchg_ptr (THREAD, &_owner, NULL) != NULL) {



            return ;



         }



         TEVENT (Exit - Reacquired) ;



      } else {



         if ((intptr_t(_EntryList)|intptr_t(_cxq)) == 0 || _succ != NULL) {



            OrderAccess::release_store_ptr (&_owner, NULL) ;  



            OrderAccess::storeload() ;



            if (_cxq == NULL || _succ != NULL) {



                TEVENT (Inflated exit - simple egress) ;



                return ;



            }



            if (Atomic::cmpxchg_ptr (THREAD, &_owner, NULL) != NULL) {



               TEVENT (Inflated exit - reacquired succeeded) ;



               return ;



            }



            TEVENT (Inflated exit - reacquired failed) ;



         } else {
            TEVENT (Inflated exit - complex egress) ;
         }
      }
      ObjectWaiter * w = NULL ;
      int QMode = Knob_QMode ;
      if (QMode == 2 && _cxq != NULL) {
          /**
          *模式2:cxq队列的优先权大于EntryList，直接从cxq队列中取出一个线程结点，准备唤醒
          **/
          w = _cxq ;
          ExitEpilog (Self, w) ;
          return ;
      }
      if (QMode == 3 && _cxq != NULL) {
          /**
          *模式3:将cxq队列插入到_EntryList尾部
          **/
          w = _cxq ;
          for (;;) {
             //CAS操作取出cxq队列首结点
             ObjectWaiter * u = (ObjectWaiter *) Atomic::cmpxchg_ptr (NULL, &_cxq, w) ;
             if (u == w) break ;
             w = u ; //更新w，自旋
          }
          ObjectWaiter * q = NULL ;
          ObjectWaiter * p ;
          for (p = w ; p != NULL ; p = p->_next) {
              guarantee (p->TState == ObjectWaiter::TS_CXQ, "Invariant") ;
              p->TState = ObjectWaiter::TS_ENTER ; //改变ObjectWaiter状态
              //下面两句为cxq队列反向构造一条链，即将cxq变成双向链表
              p->_prev = q ;
              q = p ;
          }
          ObjectWaiter * Tail ;
          //获得_EntryList尾结点
          for (Tail = _EntryList ; Tail != NULL && Tail->_next != NULL ; Tail = Tail->_next) ;
          if (Tail == NULL) {
              _EntryList = w ;//_EntryList为空，_EntryList=w
          } else {
              //将w插入_EntryList队列尾部
              Tail->_next = w ;
              w->_prev = Tail ;
          }
   }
      if (QMode == 4 && _cxq != NULL) {
         /**
         *模式四：将cxq队列插入到_EntryList头部
         **/
          w = _cxq ;
          for (;;) {
             ObjectWaiter * u = (ObjectWaiter *) Atomic::cmpxchg_ptr (NULL, &_cxq, w) ;
             if (u == w) break ;
             w = u ;
          }
          ObjectWaiter * q = NULL ;
          ObjectWaiter * p ;
          for (p = w ; p != NULL ; p = p->_next) {
              guarantee (p->TState == ObjectWaiter::TS_CXQ, "Invariant") ;
              p->TState = ObjectWaiter::TS_ENTER ;
              p->_prev = q ;
              q = p ;
          }
          if (_EntryList != NULL) {
            //q为cxq队列最后一个结点
              q->_next = _EntryList ;
              _EntryList->_prev = q ;
          }
          _EntryList = w ;
       }
      w = _EntryList  ;
      if (w != NULL) {
          ExitEpilog (Self, w) ;//从_EntryList中唤醒线程
          return ;
      }
      w = _cxq ;
      if (w == NULL) continue ; //如果_cxq和_EntryList队列都为空，自旋
      for (;;) {
          //自旋再获得cxq首结点
          ObjectWaiter * u = (ObjectWaiter *) Atomic::cmpxchg_ptr (NULL, &_cxq, w) ;
          if (u == w) break ;
          w = u ;
      }
      /**
      *下面执行的是：cxq不为空，_EntryList为空的情况
      **/
      if (QMode == 1) {//结合前面的代码，如果QMode == 1，_EntryList不为空，直接从_EntryList中唤醒线程
         // QMode == 1 : drain cxq to EntryList, reversing order
         // We also reverse the order of the list.
         ObjectWaiter * s = NULL ;
         ObjectWaiter * t = w ;
         ObjectWaiter * u = NULL ;
         while (t != NULL) {
             guarantee (t->TState == ObjectWaiter::TS_CXQ, "invariant") ;
             t->TState = ObjectWaiter::TS_ENTER ;
             //下面的操作是双向链表的倒置
             u = t->_next ;
             t->_prev = u ;
             t->_next = s ;
             s = t;
             t = u ;
         }
         _EntryList  = s ;//_EntryList为倒置后的cxq队列
      } else {
         // QMode == 0 or QMode == 2
         _EntryList = w ;
         ObjectWaiter * q = NULL ;
         ObjectWaiter * p ;
         for (p = w ; p != NULL ; p = p->_next) {
             guarantee (p->TState == ObjectWaiter::TS_CXQ, "Invariant") ;
             p->TState = ObjectWaiter::TS_ENTER ;
             //构造成双向的
             p->_prev = q ;
             q = p ;
         }
      }
      if (_succ != NULL) continue;
      w = _EntryList  ;
      if (w != NULL) {
          ExitEpilog (Self, w) ; //从_EntryList中唤醒线程
          return ;
      }
   }
}
```

ExitEpilog用来唤醒线程，代码如下：

```
void ObjectMonitor::ExitEpilog (Thread * Self, ObjectWaiter * Wakee) {
   assert (_owner == Self, "invariant") ;
   _succ = Knob_SuccEnabled ? Wakee->_thread : NULL ;
   ParkEvent * Trigger = Wakee->_event ;
   Wakee  = NULL ;
   OrderAccess::release_store_ptr (&_owner, NULL) ;
   OrderAccess::fence() ;                            
   if (SafepointSynchronize::do_call_back()) {
      TEVENT (unpark before SAFEPOINT) ;
   }
   DTRACE_MONITOR_PROBE(contended__exit, this, object(), Self);
   Trigger->unpark() ; //唤醒线程
   // Maintain stats and report events to JVMTI
   if (ObjectMonitor::_sync_Parks != NULL) {
      ObjectMonitor::_sync_Parks->inc() ;
   }
}
```

重量级锁出口流程图：
![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly93d3cubGludXhpZGMuY29tL3VwbG9hZC8yMDE4XzAyLzE4MDIwNjIxNTM3MjMzOC5wbmc?x-oss-process=image/format,png)

 自旋

通过对源码的分析，发现多处存在自旋和tryLock操作，那么这些操作好不好，如果tryLock过少，大部分线程都会挂起，因为在拥有对象锁的线程释放锁后不能及时感知，导致用户态和核心态状态转换较多，效率低下，极限思维就是：没有自旋，所有线程挂起，如果tryLock过多，存在两个问题：1. 即使自旋避免了挂起，但是自旋的代价超过了挂起，得不偿失，那我还不如不要自旋了。 2. 如果自旋仍然不能避免大部分挂起的话，那就是又自旋又挂起，效率太低。极限思维就是：无限自旋，白白浪费了cpu资源，所以在代码中每个自旋和tryLock的插入应该都是经过测试后决定的。

编译期间锁优化

锁消除

还是先看一下简洁的代码

```
public class test {
    public String test(String s1,String s2) {
        return s1+s2;
 }
}
```

javac javap后：

```
public class test {
  public test();
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return
  public java.lang.String test(java.lang.String, java.lang.String);
    Code:
       0: new           #2                  // class java/lang/StringBuilder
       3: dup
       4: invokespecial #3                  // Method java/lang/StringBuilder."<init>":()V
       7: aload_1
       8: invokevirtual #4                  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
      11: aload_2
      12: invokevirtual #4                  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
      15: invokevirtual #5                  // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
      18: areturn
}
```

上述字节码等价成java代码为：

```
public class test {
    public String test(String s1,String s2) {
        StringBuilder sb = new StringBuilder();
        sb.append(s1);
        sb.append(s2);
        return sb.toString();
 }
}
```

sb的append方法是同步的，但是sb是在方法内部，每个运行的线程都会实例化一个StringBuilder对象，在私有栈持有该对象引用(其他线程无法得到)，也就是说sb不存在多线程访问，那么在jvm运行期间，即时编译器就会将锁消除

锁粗化

将前面的代码稍微变一下：

```
public class test {
    StringBuilder sb = new StringBuilder();
    public String test(String s1,String s2) {
        sb.append(s1);
        sb.append(s2);
        return sb.toString();
 }
}
```

首先可以确定的是这段代码不能锁消除优化，因为sb是类的实例变量，会被多线程访问，存在线程安全问题，那么访问test方法的时候就会对sb对象，加锁，解锁，加锁，解锁，很显然这一过程将会大大降低效率，因此在即时编译的时候会进行锁粗化，在sb.appends(s1)之前加锁，在sb.append(s2)执行完后释放锁。

**总结**

**引入偏向锁的目的：**在只有单线程执行情况下，尽量减少不必要的轻量级锁执行路径，轻量级锁的获取及释放依赖多次CAS原子指令，而偏向锁只依赖一次CAS原子指令置换ThreadID，之后只要判断线程ID为当前线程即可，偏向锁使用了一种等到竞争出现才释放锁的机制，消除偏向锁的开销还是蛮大的。如果同步资源或代码一直都是多线程访问的，那么消除偏向锁这一步骤对你来说就是多余的，可以通过-XX:-UseBiasedLocking=false来关闭
**引入轻量级锁的目的：**在多线程交替执行同步块的情况下，尽量避免重量级锁引起的性能消耗(用户态和核心态转换)，但是如果多个线程在同一时刻进入临界区，会导致轻量级锁膨胀升级重量级锁，所以轻量级锁的出现并非是要替代重量级锁
**重入:**对于不同级别的锁都有重入策略，偏向锁:单线程独占，重入只用检查threadId等于该线程；轻量级锁：重入将栈帧中lock record的header设置为null，重入退出，只用弹出栈帧，直到最后一个重入退出CAS写回数据释放锁；重量级锁：重入_recursions++，重入退出_recursions--，_recursions=0时释放锁











## END