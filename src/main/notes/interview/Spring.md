# Spring

------

## 一、IOC

`Ioc—Inversion of Control`，即“控制反转”，不是什么技术，而是一种设计思想。在Java 开发中，**Ioc意味着将你设计好的对象交给容器控制，而不是传统的在你的对象内部直接控制**。如何理解好Ioc呢？理解好Ioc的关键是要明确“谁控制谁，控制什么，为何是反转（有反转就应该有正转了），哪些方面反转了”，那我们来深入分析一下：

- **谁控制谁，控制什么**：传统Java SE程序设计，我们直接在对象内部通过new进行创建对象，是程序主动去创建依赖对象；而IoC是有专门一个容器来创建这些对象，即由Ioc容器来控制对象的创建；谁控制谁？当然是IoC 容器控制了对象；控制什么？那就是主要控制了外部资源获取（不只是对象包括比如文件等）。
- **为何是反转，哪些方面反转了**：有反转就有正转，传统应用程序是由我们自己在对象中主动控制去直接获取依赖对象，也就是正转；而反转则是由容器来帮忙创建及注入依赖对象；为何是反转？因为由容器帮我们查找及注入依赖对象，对象只是被动的接受依赖对象，所以是反转；哪些方面反转了？依赖对象的获取被反转了。

### 1、IoC能做什么

**IoC 不是一种技术，只是一种思想，一个重要的面向对象编程的法则，它能指导我们如何设计出松耦合、更优良的程序**。传统应用程序都是由我们在类内部主动创建依赖对象，从而导致类与类之间高耦合，难于测试；有了IoC容器后，把创建和查找依赖对象的控制权交给了容器，由容器进行注入组合对象，所以对象与对象之间是 松散耦合，这样也方便测试，利于功能复用，更重要的是使得程序的整个体系结构变得非常灵活。

### 2、IoC和DI

`DI—Dependency Injection`，即“依赖注入”：组件之间依赖关系由容器在运行期决定，形象的说，即由容器动态的将某个依赖关系注入到组件之中。依赖注入的目的并非为软件系统带来更多功能，而是为了提升组件重用的频率，并为系统搭建一个灵活、可扩展的平台。通过依赖注入机制，我们只需要通过简单的配置，而无需任何代码就可指定目标需要的资源，完成自身的业务逻辑，而不需要关心具体的资源来自何处，由谁实现。

理解DI的关键是：“谁依赖谁，为什么需要依赖，谁注入谁，注入了什么”，那我们来深入分析一下：

- **谁依赖于谁**：当然是应用程序依赖于IoC容器；
- **为什么需要依赖**：应用程序需要IoC容器来提供对象需要的外部资源；
- **谁注入谁**：很明显是IoC容器注入应用程序某个对象，应用程序依赖的对象；
- **注入了什么**：就是注入某个对象所需要的外部资源（包括对象、资源、常量数据）。

IoC和DI由什么关系呢？其实它们是同一个概念的不同角度描述，由于控制反转概念比较含糊（可能只是理解为容器控制对象这一个层面，很难让人想到谁来维护对象关系），所以2004年大师级人物Martin Fowler又给出了一个新的名字：“依赖注入”，**相对IoC 而言，“依赖注入”明确描述了“被注入对象依赖IoC容器配置依赖对象”**。

### 3、IOC vs Factory

简单来说，IOC 与 工厂模式 分别代表了 push 与 pull 的机制：

- Pull 机制：类间接依赖于 Factory Method ，而 Factory Method 又依赖于具体类。
- Push 机制：容器可以在一个位置配置所有相关组件，从而促进高维护和松耦合。

**使用 工厂模式 的责任仍然在于类（尽管间接地）来创建新对象，而 依赖注入 将责任外包**。

### 4、循环依赖

Spring 为了解决单例的循环依赖问题，使用了 三级缓存 ，递归调用时发现 Bean 还在创建中即为循环依赖

```
/** 一级缓存：用于存放完全初始化好的 bean **/
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<String, Object>(256);

/** 二级缓存：存放原始的 bean 对象（尚未填充属性），用于解决循环依赖 */
private final Map<String, Object> earlySingletonObjects = new HashMap<String, Object>(16);

/** 三级级缓存：存放 bean 工厂对象，用于解决循环依赖 */
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<String, ObjectFactory<?>>(16);

/**
bean 的获取过程：先从一级获取，失败再从二级、三级里面获取

创建中状态：是指对象已经 new 出来了但是所有的属性均为 null 等待被 init
*/
```

1. A 创建过程中需要 B，于是 A 将自己放到三级缓里面 ，去实例化 B
2. B 实例化的时候发现需要 A，于是 B 先查一级缓存，没有，再查二级缓存，还是没有，再查三级缓存，找到了！
   1. 然后把三级缓存里面的这个 A 放到二级缓存里面，并删除三级缓存里面的 A
   2. B 顺利初始化完毕，将自己放到一级缓存里面（此时B里面的A依然是创建中状态）
3. 然后回来接着创建 A，此时 B 已经创建结束，直接从一级缓存里面拿到 B ，然后完成创建，并将自己放到一级缓存里面
4. 如此一来便解决了循环依赖的问题

## 二、设计模式

- 代理模式：AOP
- 单例模式：默认 Bean 为单例
- 工厂模式：BeanFactory
- IOC：依赖倒置 or 依赖注入
- MVC：spring web
- 模版方法模式：JdbcTemplate



## 三、面试题

### 1、你知道spring循环依赖问题么

见  一.4

### 2、还有spring mvc的工作过程

入口是哪，怎么经过拦截器，过滤器，怎么解析参数，怎么到controller，怎么渲染view返回？

1、springmvc工作原理图

![img](https://img2018.cnblogs.com/blog/1121080/201905/1121080-20190509202147059-745656946.jpg)

2、springmvc工作流程

1、 用户向服务端发送一次请求，这个请求会先到前端控制器DispatcherServlet(也叫中央控制器)。
2、DispatcherServlet接收到请求后会调用HandlerMapping处理器映射器。由此得知，该请求该由哪个Controller来处理（并未调用Controller，只是得知）
3、DispatcherServlet调用HandlerAdapter处理器适配器，告诉处理器适配器应该要去执行哪个Controller
4、HandlerAdapter处理器适配器去执行Controller并得到ModelAndView(数据和视图)，并层层返回给DispatcherServlet
5、DispatcherServlet将ModelAndView交给ViewReslover视图解析器解析，然后返回真正的视图。
6、DispatcherServlet将模型数据填充到视图中
7、DispatcherServlet将结果响应给用户

3、组件说明

- DispatcherServlet：前端控制器，也称为中央控制器，它是整个请求响应的控制中心，组件的调用由它统一调度。
- HandlerMapping：处理器映射器，它根据用户访问的 URL 映射到对应的后端处理器 Handler。也就是说它知道处理用户请求的后端处理器，但是它并不执行后端处理器，而是将处理器告诉给中央处理器。
- HandlerAdapter：处理器适配器，它调用后端处理器中的方法，返回逻辑视图 ModelAndView 对象。
- ViewResolver：视图解析器，将 ModelAndView 逻辑视图解析为具体的视图（如 JSP）。
- Handler：后端处理器，对用户具体请求进行处理，也就是我们编写的 Controller 类。

### 3、Spring @Transaction失效场景

参考：<https://blog.csdn.net/justLym/article/details/105040531>

**3.1、@Transcational可以作用在接口、类、类方法。**

作用类：当把@Transcational注解放在类上时，表示所有该类的public方法都配置上相同的事物属性
作用方法：当配置了@Transcational，方法也配置了@Transcational,那么方法上的事物属性会覆盖掉类上声明的事物属性。
作用接口：不推荐这种使用方式，因为一旦标注在Interface上并且配置了Spring AOP使用CGLib动态代理，将会导致@Transcational失效
3.2、**@Transcational`有哪些属性**

3.2.1、`propagation`属性

propagation代表事物传播行为，默认值为Propagation.REQUIRE

- Propagation.REQUIRE：如果当前存在事物，则加入当前事物，如果不存在事物，则创建一个新的事物。（也就是说，A和B方法都添加了事物注解，在A方法调用了B方法，那么B方法则会加入A方法的事物当中去，把这两个事物合并成一个事物。）
- Propagation.SUPPORTS：如果当前存在事物，则加入当前事物运行，如果不存在事物，则以非事物方式运行。
- Propagation.MANDATORY：如果当前存在事物，则加入当前事物运行，如果不存在事物，则抛出异常。
- Propagation.REQUIRES_NEW：重新创建一个新的事物，如果当前存在事物，暂停当前事物。（当类A中的a方法用默认事物传播行为
- Propagation.REQUIRE，类B中的b方法加上采用Propagation.REQUIRES_NEW事物传播行为，然后在a方法中调用b方法，然而a方法抛出异常了，b方法并没有回滚，因为Propagation.REQUIRES_NEW会暂停a方法中的事物）
- Propagation.NOT_SUPPORTED：以非事物的方式运行，如果当前存在事物，则暂停当前事物。
- Propagation.NEVER：始终以非事物的方式运行，如果当前存在事物，则抛出异常。
- Propagation.NESTED：和Propagation.REQUIRE效果一样。

3.2.2、`isolation`属性

isolation:事物的隔离级别，默认值为Isolation.DEFAULT

- Isolation.DEFAULT：使用数据库默认的隔离级别
- Isolation.READ_UNCOMMITTED：读未提交
- Isolation.READ_COMMITTED：读已提交
- Isolation.REPEATABLE_READ：可重复读
- Isolation.SERIALIZABLE：串行化

3.2.3、`timeout`属性

timout：事物超时时间，默认为-1。如果超过等待的时间但事物还没有完成，自动回滚事物

3.2.4、`readOnly`属性

readOnly：指定事物是否为只读事物，默认值为false；为了忽略那些不需要事物的方法，比如读取数据，可以设置read-only为true。

3.2.5、`rollbackFor`属性

rollbackFor：抛出指定的异常类型，回滚事物，也可以指定多个异常类型

3.2.6、`noRollbackFor`属性

noRollbackFor：抛出指定的异常类型，不会滚事物，也可以指定多个异常类型

**3.3、`@Transcational`失效的场景**

结合具体代码分析哪些场景，@Transcational注解会失效

3.3.1、`@Transcational`应用在非public修饰的方法上，Transcational将会失效。

![拦截器](https://img-blog.csdnimg.cn/20200323140227253.jpg?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2p1c3RMeW0=,size_16,color_FFFFFF,t_70)

之所以会失效是因为在Spring AOP代理时，如上图所示TranscationInterceptor（事物拦截器）在目标方法执行前后进行拦截，DynamicAdvisedInterceptor（CglibAopProxy的内部类）的intercept方法或JdkDynamicAopPorxy的invoke方法间接调用
AbstractFallbackTransactionAttributeSource的 computeTransactionAttribute 方法，获取Transactional 注解的事务配置信息。

```java
protected TransactionAttribute computeTransactionAttribute(Method method,
    Class<?> targetClass) {
        // Don't allow no-public methods as required.
        if (allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
        return null;
}
```

此方法会检查目标方法的修饰符是否为 public，不是 public则不会获取@Transactional 的属性配置信息。
注意：protected、private 修饰的方法上使用 @Transactional 注解，虽然事务无效，但不会有任何报错，这是我们很容犯错的一点。

3.3.2、@Transcational注解属性propagation设置错误，事务将不会发生回滚。

- TransactionDefinition.PROPAGATION_SUPPORTS：如果当前存在事务，则加入该事务；如果当前没有事务，则以非事务的方式继续运行
- TransactionDefinition.PROPAGATION_NOT_SUPPORTED：以非事务方式运行，如果当前存在事务，则把当前事务挂起。
- TransactionDefinition.PROPAGATION_NEVER：以非事务方式运行，如果当前存在事务，则抛出异常。

3.3.3、@Transactional 注解属性 rollbackFor 设置错误

rollbackFor 可以指定能够触发事务回滚的异常类型。Spring默认抛出了未检查unchecked异常（继承自 RuntimeException 的异常）或者 Error才回滚事务；其他异常不会触发回滚事务。如果在事务中抛出其他类型的异常，但却期望 Spring 能够回滚事务，就需要指定 rollbackFor属性。

```java
// 希望自定义的异常可以进行回滚
@Transactional(propagation= Propagation.REQUIRED,rollbackFor= MyException.class
```

若在目标方法中抛出的异常是 rollbackFor 指定的异常的子类，事务同样会回滚。Spring源码如下：

```java
private int getDepth(Class<?> exceptionClass, int depth) {
    if (exceptionClass.getName().contains(this.exceptionName)) {
        // Found it!
        return depth;
    }
    // If we've gone as far as we can go and haven't found it...
    if (exceptionClass == Throwable.class) {
        return -1;
    }
	return getDepth(exceptionClass.getSuperclass(), depth + 1);
}
```

3.3.4、同一个类中方法调用，导致@Transactional失效

开发中避免不了会对同一个类里面的方法调用，比如有一个类Test，它的一个方法A，A再调用本类的方法B（不论方法B是用public还是private修饰），但方法A没有声明注解事务，而B方法有。则外部调用方法A之后，方法B的事务是不会起作用的。这也是经常犯错误的一个地方。

那为啥会出现这种情况？其实这还是由于使用Spring AOP代理造成的，因为只有当事务方法被当前类以外的代码调用时，才会由Spring生成的代理对象来管理。

```java
//@Transactional
    @GetMapping("/test")
    private Integer A() throws Exception {
        CityInfoDict cityInfoDict = new CityInfoDict();
        cityInfoDict.setCityName("2");
        /**
         * B 插入字段为 3的数据
         */
        this.insertB();
        /**
         * A 插入字段为 2的数据
         */
        int insert = cityInfoDictMapper.insert(cityInfoDict);

        return insert;
    }

    @Transactional()
    public Integer insertB() throws Exception {
        CityInfoDict cityInfoDict = new CityInfoDict();
        cityInfoDict.setCityName("3");
        cityInfoDict.setParentCityId(3);

        return cityInfoDictMapper.insert(cityInfoDict);
    }
```

3.3.5、异常被 try-catch捕获，导致@Transactional失效

这种情况是最常见的一种@Transactional注解失效场景，

```java
@Transactional
    private Integer A() throws Exception {
        int insert = 0;
        try {
            CityInfoDict cityInfoDict = new CityInfoDict();
            cityInfoDict.setCityName("2");
            cityInfoDict.setParentCityId(2);
            /**
             * A 插入字段为 2的数据
             */
            insert = cityInfoDictMapper.insert(cityInfoDict);
            /**
             * B 插入字段为 3的数据
             */
            b.insertB();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

如果B方法内部抛了异常，而A方法此时try catch了B方法的异常，那这个事务还能正常回滚吗？

答案：不能！

会抛出异常：

```java
org.springframework.transaction.UnexpectedRollbackException: Transaction rolled back because it has been marked as rollback-only
```

因为当ServiceB中抛出了一个异常以后，ServiceB标识当前事务需要rollback。但是ServiceA中由于你手动的捕获这个异常并进行处理，ServiceA认为当前事务应该正常commit。此时就出现了前后不一致，也就是因为这样，抛出了前面的UnexpectedRollbackException异常。

spring的事务是在调用业务方法之前开始的，业务方法执行完毕之后才执行commit or rollback，事务是否执行取决于是否抛出runtime异常。如果抛出runtime exception 并在你的业务方法中没有catch到的话，事务会回滚。

在业务方法中一般不需要catch异常，如果非要catch一定要抛出throw new RuntimeException()，或者注解中指定抛异常类型@Transactional(rollbackFor=Exception.class)，否则会导致事务失效，数据commit造成数据不一致，所以有些时候try catch反倒会画蛇添足。

3.3.6、数据库引擎不支持事务，导致@Transactional失效

如果使用 MySQL 且引擎是 MyISAM，则事务会不起作用，原因是 MyISAM 不支持事务，改成 InnoDB 引擎则支持事务。

## 参考链接



