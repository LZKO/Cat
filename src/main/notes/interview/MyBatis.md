# MyBatis

------

## 一、面试题

### 1、#{}和${}的区别

`#{}`是预编译处理，`${}`是字符串替换。

Mybatis 在处理 `#{}` 时，会将sql中的 `#{}` 替换为 `?` 号，调用 `PreparedStatement` 的 `set` 方法来赋值；

Mybatis在处理`${}`时，就是把`${}`替换成变量的值。

使用`#{}`可以有效的防止SQL注入，提高系统安全性。

### 2、通常一个Xml映射文件，都会写一个Dao接口与之对应，请问，这个Dao接口的工作原理是什么？Dao接口里的方法，参数不同时，方法能重载吗？

Dao 接口，就是人们常说的 Mapper 接口，接口的全限名，就是映射文件中的 `namespace` 的值，接口的方法名，就是映射文件中 `MappedStatement` 的 id 值，接口方法内的参数，就是传递给sql的参数。

Mapper 接口是没有实现类的，当调用接口方法时，`接口全限名+方法名` 拼接字符串作为 key 值，可唯一定位一个MappedStatement。

在Mybatis中，每一个`<select>、<insert>、<update>、<delete>` 标签，都会被解析为一个 `MappedStatement` 对象。

Dao接口里的方法，是 **不能重载** 的，因为是全限名+方法名的保存和寻找策略。

Dao接口的工作原理是 JDK 动态代理， Mybatis 运行时会使用 JDK 动态代理为 Dao 接口生成代理 proxy 对象，代理对象 proxy 会拦截接口方法，转而执行 `MappedStatement` 所代表的sql，然后将sql执行结果返回。

## 二、缓存机制

Mybatis 的缓存均缓存查询操作结果。按照作用域范围，可以分为：

```
- **一级缓存**： `SqlSession` 级别的缓存
- **二级缓存**： `namespace` 级别的缓存
```

### 1、一级缓存

Mybatis 默认开启了一级缓存， 一级缓存有两个级别可以设置：分别是 `SESSION` 或者 `STATEMENT` 默认是 `SESSION` 级别，即在一个 MyBatis会话中执行的所有语句，都会共享这一个缓存。一种是 `STATEMENT` 级别，可以理解为缓存只对当前执行的这一个 `Statement` 有效。

> STATEMENT 级别相当于关闭一级缓存

```
<setting name="localCacheScope" value="SESSION"/>
```

#### 1.1、基本原理

![img](https://www.hadyang.xyz/interview/docs/fromwork/mybatis/cache/images/2019-04-05-22-04-22.png)

在一级缓存中，当 `sqlSession` 执行写操作（执行插入、更新、删除），清空 `SqlSession` 中的一级缓存。

#### 1.2、总结

- MyBatis 一级缓存的生命周期和SqlSession一致。
- MyBatis 一级缓存内部设计简单，只是一个没有容量限定的HashMap，在缓存的功能性上有所欠缺。
- MyBatis 的一级缓存最大范围是SqlSession内部，有多个SqlSession或者分布式的环境下，数据库写操作会引起脏数据，建议设定缓存级别为Statement。

### 2、二级缓存

如果多个 SqlSession 之间需要共享缓存，则需要使用到二级缓存。开启二级缓存后，会使用 CachingExecutor 装饰 Executor ，进入一级缓存的查询流程前，先在C achingExecutor 进行二级缓存的查询，具体的工作流程如下所示。

![img](https://www.hadyang.xyz/interview/docs/fromwork/mybatis/cache/images/2019-04-05-22-10-04.png)

二级缓存开启后，同一个namespace下的所有操作语句，都影响着同一个Cache，即二级缓存被多个SqlSession共享，是一个全局的变量。当开启缓存后，数据的查询执行的流程就是 `二级缓存 -> 一级缓存 -> 数据库`。

```
<setting name="cacheEnabled" value="true"/>
```

#### 2.1、总结

- MyBatis 的二级缓存相对于一级缓存来说，实现了 SqlSession 之间缓存数据的共享，同时粒度更加的细，能够到 namespace 级别，通过 Cache 接口实现类不同的组合，对Cache的可控性也更强。
- MyBatis 在多表查询时，极大可能会出现脏数据，有设计上的缺陷，安全使用二级缓存的条件比较苛刻。
- 在分布式环境下，由于默认的 MyBatis Cache 实现都是基于本地的，分布式环境下必然会出现读取到脏数据，需要使用集中式缓存将 MyBatis 的 Cache 接口实现，有一定的开发成本，直接使用 Redis、Memcached 等分布式缓存可能成本更低，安全性也更高。

## 三、动态代理

待补充

### 1、获取代理类流程

获取Mapper代理类的时序图如下：

![image](https://www.hadyang.xyz/interview/docs/fromwork/mybatis/proxy/images/fecd42f80994ebfa775ea5e56166249b.png)

重点说下MapperProxy类，声明如下：

```
public class MapperProxy<T> implements InvocationHandler, Serializable
```

获取到 `MapperProxy` 之后，根据调用不同的方法，会将最终的参数传递给 `SqlSession`。





## 参考链接

[Mybatis 的常见面试题](https://blog.csdn.net/eaphyy/article/details/71190441)

