# Zookeeper

[参考链接](<https://www.hadyang.xyz/interview/docs/architecture/distributed/zk/>)

------

ZK 不是解决分布式问题的银弹

## 一、分布式应用

分布式应用可以在给定时间（同时）在网络中的多个系统上运行，通过协调它们以快速有效的方式完成特定任务。通常来说，**对于复杂而耗时的任务，非分布式应用（运行在单个系统中）需要几个小时才能完成，而分布式应用通过使用所有系统涉及的计算能力可以在几分钟内完成**。

通过将分布式应用配置为在更多系统上运行，可以进一步减少完成任务的时间。分布式应用正在运行的一组系统称为 **集群**，而在集群中运行的每台机器被称为 **节点**。

### 1、分布式应用的优点

- 可靠性：单个或几个系统的故障不会使整个系统出现故障。
- 可扩展性：可以在需要时增加性能，通过添加更多机器，在应用程序配置中进行微小的更改，而不会有停机时间。
- 透明性：隐藏系统的复杂性，并将其显示为单个实体/应用程序。

### 2、分布式应用的挑战

- 竞争条件：两个或多个机器尝试执行特定任务，实际上只需在任意给定时间由单个机器完成。例如，共享资源只能在任意给定时间由单个机器修改。
- 死锁：两个或多个操作等待彼此无限期完成。
- 不一致：数据的部分失败。

## 二、ZooKeeper基础

Apache ZooKeeper是由集群（节点组）使用的一种服务，用于在自身之间协调，并通过稳健的同步技术维护共享数据。ZooKeeper本身是一个分布式应用程序，为写入分布式应用程序提供服务。

ZooKeeper 的好处：

- 简单的分布式协调过程
- 同步：服务器进程之间的相互排斥和协作。
- 有序性
- 序列化：根据特定规则对数据进行编码(Jute)。
- 可靠性
- 原子性：数据转移完全成功或完全失败，但没有事务是部分的。

### 1、架构

### 2、数据模型

### 3、Znode

### 4、Sessions

### 5、Watcher

## 三、Zookeeper 工作流程

### 1、ZooKeeper Service 节点数量的影响

## 四、ZAB 协议

### 1、消息广播

### 2、崩溃恢复

## 五、应用场景

### 1、发布订阅

### 2、命名服务

### 3、协调分布式事务

### 4、分布式锁

## 六、ZooKeeper 的缺陷

### 1、zookeeper 不是为高可用性设计的

### 2、zookeeper 的选举过程速度很慢

### 3、zookeeper 的性能是有限的

### 4、zookeeper 无法进行有效的权限控制

### 5、即使有了 zookeeper 也很难避免业务系统的数据不一致

### 6、Zookeeper 并不保证读取的是最新数据

### 7、我们能做什么

## 七、FAQ

### 1、客户端对 ServerList 的轮询机制是什么

### 2、客户端如何正确处理 CONNECTIONLOSS (连接断开) 和 SESSIONEXPIRED (Session 过期)两类连接异常

### 3、一个客户端修改了某个节点的数据，其它客户端能够马上获取到这个最新数据吗

### 4、ZK为什么不提供一个永久性的Watcher注册机制

### 5、使用watch需要注意的几点

### 6、我能否收到每次节点变化的通知

### 7、能为临时节点创建子节点吗

### 8、是否可以拒绝单个IP对ZK的访问,操作

### 9、在[`getChildren(String path, boolean watch)`]注册对节点子节点的变化，那么子节点的子节点变化能通知吗

### 10、创建的临时节点什么时候会被删除，是连接一断就删除吗？延时是多少？

### 11、ZooKeeper集群中个服务器之间是怎样通信的？

## 八、参考文档

- [ZooKeeper FAQ](http://jm.taobao.org/2013/10/07/zookeeper-faq/)
- [Apache ZooKeeper数据模型](https://www.cnblogs.com/IcanFixIt/p/7818592.html)
- [Zookeeper并不保证读取的是最新数据](http://www.crazyant.net/2120.html)
- [详解分布式协调服务 ZooKeeper](https://draveness.me/zookeeper-chubby)
- [ZooKeeper 架构](https://zookeeper.apache.org/doc/r3.1.2/zookeeperProgrammers.html)
- [阿里巴巴为什么不用ZooKeeper 做服务发现？](http://jm.taobao.org/2018/06/13/%E5%81%9A%E6%9C%8D%E5%8A%A1%E5%8F%91%E7%8E%B0%EF%BC%9F/)
- [ZooKeeper 技术内幕：Leader 选举](http://ningg.top/zookeeper-lesson-2-leader-election/)

