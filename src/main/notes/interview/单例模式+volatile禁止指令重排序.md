# 单例模式+volatile禁止指令重排序

## 一、概述

单例模式：

单例，顾名思义就是只能有一个、不能再出现第二个。就如同地球上没有两片一模一样的树叶一样。

在这里就是说：一个类只能有一个实例，并且整个项目系统都能访问该实例。

单例模式共分为两大类：

- 懒汉模式：实例在第一次使用时创建
- 饿汉模式：实例在类装载时创建

## 二、饿汉模式

按照定义我们可以写出一个基本代码：

```
public class Singleton {

 // 使用private将构造方法私有化，以防外界通过该构造方法创建多个实例
 private Singleton() {
 }

 // 由于不能使用构造方法创建实例，所以需要在类的内部创建该类的唯一实例
 // 使用static修饰singleton 在外界可以通过类名调用该实例   类名.成员名
 static Singleton singleton = new Singleton();   // 1
 
 // 如果使用private封装该实例，则需要添加get方法实现对外界的开放
 private static Singleton instance = new Singleton();    // 2
 // 添加static，将该方法变成类所有   通过类名访问
 public static Singleton getInstance(){
   return instance;
 }
 //1和2选一种即可，推荐2
}
```

 对于饿汉模式来说，这种写法已经很‘perfect’了，唯一的缺点就是，由于instance的初始化是在类加载时进行的，类加载是由ClassLoader来实现的，如果初始化太早，就会造成资源浪费。

当然，如果所需的单例占用的资源很少，并且也不依赖于其他数据，那么这种实现方式也是很好的。

**类装载的时机：**

- new一个对象时
- 使用反射创建它的实例时
- 子类被加载时，如果父类还没有加载，就先加载父类
- JVM启动时执行主类 会先被加载

## 三、懒汉模式

懒汉模式的代码如下

```
// 代码一
public class Singleton {
   private static Singleton instance = null;
   private Singleton(){
   }
   public static Singleton getInstance() {
       if (instance == null) {
           instance = new Singleton(); 
       }        
       return instance; 
  }
} 
```

每次获取instance之前先进行判断，如果instance为空就new一个出来，否则就直接返回已存在的instance。

这种写法在单线程的时候是没问题的。但是，当有多个线程一起工作的时候，如果有两个线程同时运行到 if (instance == null)，都判断为null（第一个线程判断为空之后，并没有继续向下执行，当第二个线程判断的时候instance依然为空），最终两个线程就各自会创建一个实例出来。这样就破环了单例模式 实例的唯一性，要想保证实例的唯一性就需要使用synchronized，加上一个同步锁：

```
// 代码二
public class Singleton {
   private static Singleton instance = null;
   private Singleton() {}
 
   public static Singleton getInstance() {
       synchronized(Singleton.class){
     if (instance == null)
       instance = new Singleton();
   }
   return instance;
   }
}
```

加上synchronized关键字之后，getInstance方法就会锁上了。如果有两个线程（T1、T2）同时执行到这个方法时，会有其中一个线程T1获得同步锁，得以继续执行，而另一个线程T2则需要等待，当第T1执行完毕getInstance之后（完成了null判断、对象创建、获得返回值之后），T2线程才会执行执行。

所以这段代码也就避免了代码一中，可能出现因为多线程导致多个实例的情况。但是，这种写法也有一个问题：给getInstance方法加锁，虽然避免了可能会出现的多个实例问题，但是会强制除T1之外的所有线程等待，实际上会对程序的执行效率造成负面影响。

### 双重检查（Double-Check）

代码二相对于代码一的效率问题，其实是为了解决1%几率的问题，而使用了一个100%出现的防护盾。那有一个优化的思路，就是把100%出现的防护盾，也改为1%的几率出现，使之只出现在可能会导致多个实例出现的地方。

代码如下：

```
// 代码三
public class Singleton {
   private static Singleton instance = null;
   private Singleton() {}
 
   public static Singleton getInstance() {
   if (instance == null){
     synchronized(Singleton.class){
       if (instance == null)
         instance = new Singleton();
     }
   }
   return instance;
   }
}
```

这段代码看起来有点复杂，注意其中有两次if(instance==null)的判断，这个叫做『双重检查 Double-Check』。

- 第一个 if(instance==null)，其实是为了解决代码二中的效率问题，只有instance为null的时候，才进入synchronized的代码段大大减少了几率。
- 第二个if(instance==null)，则是跟代码二一样，是为了防止可能出现多个实例的情况。

 这段代码看起来已经完美无瑕了。当然，只是『看起来』，还是有小概率出现问题的。想要充分理解需要先弄清楚以下几个概念：原子操作、指令重排。

 原子操作
简单来说，原子操作（atomic）就是不可分割的操作，在计算机中，就是指不会因为线程调度被打断的操作。比如，简单的赋值是一个原子操作：

```
m = 6; // 这是个原子操作
```

假如m原先的值为0，那么对于这个操作，要么执行成功m变成了6，要么是没执行 m还是0，而不会出现诸如m=3这种中间态——即使是在并发的线程中。

但是，声明并赋值就不是一个原子操作：

```
int  n=6;//这不是一个原子操作
```

对于这个语句，至少有两个操作：①声明一个变量n ②给n赋值为6——这样就会有一个中间状态：变量n已经被声明了但是还没有被赋值的状态。这样，在多线程中，由于线程执行顺序的不确定性，如果两个线程都使用m，就可能会导致不稳定的结果出现。

指令重排：
简单来说，就是计算机为了提高执行效率，会做的一些优化，在不影响最终结果的情况下，可能会对一些语句的执行顺序进行调整。比如，这一段代码：

```
int a ;   // 语句1 
a = 8 ;   // 语句2
int b = 9 ;     // 语句3
int c = a + b ; // 语句4
```

正常来说，对于顺序结构，执行的顺序是自上到下，也即1234。但是，由于指令重排的原因，因为不影响最终的结果，所以，实际执行的顺序可能会变成3124或者1324。

由于语句3和4没有原子性的问题，语句3和语句4也可能会拆分成原子操作，再重排。——也就是说，对于非原子性的操作，在不影响最终结果的情况下，其拆分成的原子操作可能会被重新排列执行顺序。

OK，了解了原子操作和指令重排的概念之后，我们再继续看代码三的问题。

主要在于singleton = new Singleton()这句，这并非是一个原子操作，事实上在 JVM 中这句话大概做了下面 3 件事情。
　　1. 给 singleton 分配内存
　　2. 调用 Singleton 的构造函数来初始化成员变量，形成实例
　　3. 将singleton对象指向分配的内存空间（执行完这步 singleton才是非 null了）

在JVM的即时编译器中存在指令重排序的优化。
也就是说上面的第二步和第三步的顺序是不能保证的，最终的执行顺序可能是 1-2-3 也可能是 1-3-2。如果是后者，则在 3 执行完毕、2 未执行之前，被线程二抢占了，这时 instance 已经是非 null 了（但却没有初始化），所以线程二会直接返回 instance，然后使用，然后顺理成章地报错。
再稍微解释一下，就是说，由于有一个『instance已经不为null但是仍没有完成初始化』的中间状态，而这个时候，如果有其他线程刚好运行到第一层if (instance ==null)这里，这里读取到的instance已经不为null了，所以就直接把这个中间状态的instance拿去用了，就会产生问题。这里的关键在于线程T1对instance的写操作没有完成，线程T2就执行了读操作。
对于代码三出现的问题，解决方案为：给instance的声明加上volatile关键字

代码如下：

```
public class Singleton {
   private static volatile Singleton instance = null;
   private Singleton() {}
 
   public static Singleton getInstance() {
   if (instance == null){
     synchronized(Singleton.class){
       if (instance == null)
         instance = new Singleton();
     }
   }
   return instance;
   }
}
```

volatile关键字的一个作用是禁止指令重排，把instance声明为volatile之后，对它的写操作就会有一个内存屏障，这样，在它的赋值完成之前，就不用会调用读操作。

**注意：volatile阻止的不是singleton = new Singleton()这句话内部[1-2-3]的指令重排，而是保证了在一个写操作（[1-2-3]）完成之前，不会调用读操作（if (instance == null)）。**



## 四、参考

[单例模式+volatile禁止指令重排序](https://www.cnblogs.com/moxiaotao/p/9945235.html)



## END