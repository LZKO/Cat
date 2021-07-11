# ArrayBlockingQueue原理分析(二)-迭代器

## 一、概述

在[上篇文章](https://www.cnblogs.com/gunduzi/p/13659370.html)的结构图中可以看出，所有的队列最后都实现了Queue接口，而Queue继承了Collection接口，而Collection接口继承了Iterable，由于不同的集合会根据自己集合的特性实现自己的迭代器，那本文就分析一下ArrayBlockingQueue集合迭代器的实现方式，因为之前都是一直使用这玩意，从来不清楚内部如何工作的，所以就拿这个集合的迭代器分析一下。

## 二、Iterable接口

```
public interface Iterable<T> {
    //顺序迭代器
    Iterator<T> iterator();
    //遍历集合，里面传入一个Consumer
    default void forEach(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        for (T t : this) {
            action.accept(t);
        }
    }
    //可分割迭代器
    default Spliterator<T> spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), 0);
     }
}
```

这个方法会返回两个迭代器，后面都会分析，中间那个是遍历集合中的元素，传入一个Consumer，意思是说对集合中的每个元素都执行一下accept方法。

## 三、Iterator接口

```
public interface Iterator<E> {
 
public interface Iterator<E> {
    //是否有下一个元素
    boolean hasNext();
    //返回当前的下一个
    E next();
    //移除，一般调用集合的remove方法进行移除
    default void remove() {
        throw new UnsupportedOperationException("remove");
    }
     //对剩余元素遍历
     default void forEachRemaining(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        while (hasNext())
            action.accept(next());
    }
 
}
```

## 四、Spliterator接口

```
public interface Spliterator<T> {
    //单个对元素执行给定的动作，如果有剩下元素未处理返回true，否则返回false
    boolean tryAdvance(Consumer<? super T> action);
    //对每个剩余元素执行给定的动作，依次处理，直到所有元素已被处理或被异常终止。默认方法调用tryAdvance方法
    default void forEachRemaining(Consumer<? super T> action) {
        do { } while (tryAdvance(action));
    }
    //对任务分割，返回一个新的Spliterator迭代器
    Spliterator<T> trySplit();
}
```

从这三个接口可以看出，第一个接口只是把下面的两个接口封装了一下，其本身基本没有定义迭代相关的方法，就只有一个遍历。Iterator和Spliterator的区别在于一个是顺序遍历，另一个是可分割的遍历，因为Java8为了加快大集合的遍历，采用了分布式的方式，先把大集合拆分成小集合，之后多线程并行遍历。本文不会介绍ArrayBlockingQueue的Spliterator迭代器，只介绍Iterator迭代器。

## 五、Itrs类

该类封装了Itr类，而Itr实现了Iterator接口。

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
class Itrs {

        /**
         * Node in a linked list of weak iterator references.
         * 定义节点，节点中包装了Itr迭代器，当Itr没有强引用存在的时候
         * 可以直接回收Itr，然后doSomeSweeping会回收无用的节点
         */
        private class Node extends WeakReference<Itr> {
            Node next;

            Node(Itr iterator, Node next) {
                super(iterator);
                this.next = next;
            }
        }

        /** Incremented whenever takeIndex wraps around to 0 */
        int cycles = 0;

        /** Linked list of weak iterator references，首节点 */
        private Node head;

        /** Used to expunge stale iterators ，要被清除的node*/
        private Node sweeper = null;

        private static final int SHORT_SWEEP_PROBES = 4;
        private static final int LONG_SWEEP_PROBES = 16;
        //构造方法，将迭代器加入链表
        Itrs(Itr initial) {
            register(initial);
        }
        //清除迭代器已经被回收的Node节点
        void doSomeSweeping(boolean tryHarder) {
            // assert lock.getHoldCount() == 1;
            // assert head != null;
            int probes = tryHarder ? LONG_SWEEP_PROBES : SHORT_SWEEP_PROBES;
            Node o, p;
            final Node sweeper = this.sweeper;
            boolean passedGo;   // to limit search to one full sweep
            //如果为null，从头开始清理
            if (sweeper == null) {
                o = null;
                p = head;
                passedGo = true;
            } else {
                o = sweeper;
                p = o.next;
                passedGo = false;
            }

            for (; probes > 0; probes--) {
                if (p == null) {
                    if (passedGo)
                        break;
                    o = null;
                    p = head;
                    passedGo = true;
                }
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.isDetached()) {
                    // found a discarded/exhausted iterator
                    //这里只要发现了需要清理的node，就重置probes，让循环一直进行下去
                    probes = LONG_SWEEP_PROBES; // "try harder"
                    // unlink p
                    //移除节点
                    p.clear();
                    p.next = null;
                    if (o == null) {
                        head = next;
                        if (next == null) {
                            // We've run out of iterators to track; retire
                            itrs = null;
                            return;
                        }
                    }
                    else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }

            this.sweeper = (p == null) ? null : o;
        }

        /**
         * Adds a new iterator to the linked list of tracked iterators.
         */
        void register(Itr itr) {
            // assert lock.getHoldCount() == 1;
            //将加入的节点放在链表的头部
            head = new Node(itr, head);
        }

        //移除节点
        void removedAt(int removedIndex) {
            for (Node o = null, p = head; p != null;) {
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.removedAt(removedIndex)) {
                    // unlink p
                    // assert it == null || it.isDetached();
                    p.clear();
                    p.next = null;
                    if (o == null)
                        head = next;
                    else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }
            if (head == null)   // no more iterators to track
                itrs = null;
        }

        /**
         * Called whenever the queue becomes empty.
         *
         * Notifies all active iterators that the queue is empty,
         * clears all weak refs, and unlinks the itrs datastructure.
         */
        void queueIsEmpty() {
            // assert lock.getHoldCount() == 1;
            for (Node p = head; p != null; p = p.next) {
                Itr it = p.get();
                if (it != null) {
                    p.clear();
                    it.shutdown();
                }
            }
            head = null;
            itrs = null;
        }

        /**
         * Called whenever an element has been dequeued (at takeIndex).
         */
        void elementDequeued() {
            // assert lock.getHoldCount() == 1;
            if (count == 0)
                queueIsEmpty();
            else if (takeIndex == 0)
                takeIndexWrapped();
        }
    }      
```

## 六、Itr内部类



从上面的分析可知，Itrs用来管理Itr，将Itr包装到一个Node节点中，然后节点中有一个指针，最终构成一个链表，其中Itr使用了弱引用包装，方便垃圾回收。Itr类是ArrayBlockingQueue内部的一个类，实现了Iterator接口，下面看一下这个类。

#### 属性

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
private class Itr implements Iterator<E> {

        /** Index to look for new nextItem; NONE at end,迭代器的迭代位置*/
        private int cursor;

        /** Element to be returned by next call to next(); null if none，下一个元素的值，这个值是队列中的值 */
        private E nextItem;

        /** Index of nextItem; NONE if none, REMOVED if removed elsewhere，下一个元素位置 */
        private int nextIndex;

        /** Last element returned; null if none or not detached. */
        private E lastItem;

        /** Index of lastItem, NONE if none, REMOVED if removed elsewhere */
        private int lastRet;
}
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

#### 构造方法

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
Itr() {
            // assert lock.getHoldCount() == 0;
            lastRet = NONE;
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                //如果队列中没有元素
                if (count == 0) {
                    // assert itrs == null;
                    cursor = NONE;
                    nextIndex = NONE;
                    prevTakeIndex = DETACHED;
                } else {
                    //初始化赋值，takeIndex为消费者消费的位置
                    final int takeIndex = ArrayBlockingQueue.this.takeIndex;
                    prevTakeIndex = takeIndex;
                    //这里就是下一个待消费数据
                    nextItem = itemAt(nextIndex = takeIndex);
                    //初始化游标，初始化为takeIndex的下一个位置
                    cursor = incCursor(takeIndex);
                    if (itrs == null) {
                       //itrs为链表的首节点，如果首节点为null，说明当前队列没有迭代器
                        itrs = new Itrs(this);
                    } else {
                        //如果有的，就把当前迭代器节点放到链表的最前面
                        itrs.register(this); // in this order
                        //清除链表中无效的迭代器节点
                        itrs.doSomeSweeping(false);
                    }
                    prevCycles = itrs.cycles;
                    // assert takeIndex >= 0;
                    // assert prevTakeIndex == takeIndex;
                    // assert nextIndex >= 0;
                    // assert nextItem != null;
                }
            } finally {
                lock.unlock();
            }
        }
```

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

从上面可以看出，构造方法，就是为里面的一些属性进行赋值，这些属性都是为了控制迭代过程的。

#### 常用方法

[![复制代码](https://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

```
        public boolean hasNext() {
            // assert lock.getHoldCount() == 0;
            //直接看属性的变量中有没有，有就返回true
            if (nextItem != null)
                return true;
            noNext();
            return false;
        }

        public E next() {
            // assert lock.getHoldCount() == 0;
            //直接从变量中拿，不从队列中获取
            final E x = nextItem;
            if (x == null)
                throw new NoSuchElementException();
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (!isDetached())
                    incorporateDequeues();
                // assert nextIndex != NONE;
                // assert lastItem == null;
                lastRet = nextIndex;
                final int cursor = this.cursor;
                if (cursor >= 0) {
                    //重新更新nextItem，如果先执行next方法，之后执行了两次take方法
                    //第二次即便take已经把第二个元素取出了，这个时候再调用next，依然为
                    //执行take方法之前的第二个元素
                    nextItem = itemAt(nextIndex = cursor);
                    // assert nextItem != null;
                    //更新游标
                    this.cursor = incCursor(cursor);
                } else {
                    nextIndex = NONE;
                    nextItem = null;
                }
            } finally {
                lock.unlock();
            }
            return x;
        }
```

## 七、总结

整个来说，迭代器原理就是使用一些变量控制到迭代的位置，当队列中的元素发生变更的时候，迭代器的控制变量跟着变动就可以，由于ArrayBlockingQueue采用了加锁的方式，所以迭代的过程中队列中删除元素是没有影响的，因为会自动修复迭代器的控制变量，而且只有一个变量修改，所以不会出现线程不安全的问题。一个队列可以有多个迭代器，迭代器被封装在Node中，最后构成一个链表，当队列中的元素更新的时候，方便统一更新迭代器的控制变量。

























## 参考

[ArrayBlockingQueue原理分析(二)-迭代器](https://www.cnblogs.com/gunduzi/p/13665396.html)



## END

