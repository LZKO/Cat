# Netty

----



## 一、BIO、NIO、AIO简单介绍

参考： [深入了解Netty【一】BIO、NIO、AIO简单介绍](<https://www.cnblogs.com/clawhub/p/11960890.html>)



## 二、零拷贝

参考：[深入了解Netty【二】零拷贝](<https://www.cnblogs.com/clawhub/p/11960918.html>)



## 三、Netty概述

参考：[深入了解Netty【三】Netty概述](<https://www.cnblogs.com/clawhub/p/11960946.html>)

### 1、简介

> Netty是一个异步事件驱动的网络应用程序框架，用于快速开发可维护的高性能协议服务器和客户端。
> Netty是一个NIO客户端服务器框架，它支持快速、简单地开发协议服务器和客户端等网络应用程序。它大大简化了网络编程，如TCP和UDP套接字服务器。
> “快速而简单”并不意味着最终的应用程序将遭遇可维护性或性能问题。Netty经过精心设计，积累了许多协议(如FTP、SMTP、HTTP和各种二进制和基于文本的遗留协议)实现的经验。因此，Netty成功地找到了一种方法，可以在不妥协的情况下实现开发、性能、稳定性和灵活性。
> [Netty官网](https://netty.io/)

![netty-components.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/77a69a49712aad862780e69b3b23edda.jpg)

### 2、应用场景

- 互联网行业
  主要用于分布式系统中的RPC,如阿里的Dubbo。
- 游戏行业
  比如地图服务器之间用Netty进行高性能通信。
- 大数据领域
  Hadoop的Netty Service。
- 开源项目
  <https://netty.io/wiki/related-projects.html>

## 四、IO模型

参考：[深入了解Netty【四】IO模型](<https://www.cnblogs.com/clawhub/p/11967366.html>)

### 引言

IO模型就是操作数据输入输出的方式，在Linux系统中有5大IO模型：阻塞式IO模型、非阻塞式IO模型、IO复用模型、信号驱动式IO模型、异步IO模型。
因为学习Netty必不可少的要了解IO多路复用模型，本篇是基础。

### 名词概念

- 阻塞：指向调用方，在调用结果返回之前，调用方线程会挂起，直到结果返回。
- 非阻塞：指向调用方，在调用结果返回之前，调用方线程会处理其他事情，不会阻塞。
- 同步：指向被调用方，被调用方得到结果后再返回给调用方。
- 异步：指向被调用方，被调用方先应答调用方，然后计算结果，最终通知并返回给调用方。
- recvfrom函数：系统调用，经socket接收数据。

### 5种IO模型

#### 1、阻塞式IO模型(blocking I/O)

![阻塞式IO模型.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/1ec1c99148bad22407b14a4fc560b1bc.jpg)

进程调用recvfrom函数，在数据没有返回之前，进程阻塞，直到数据返回后，才会处理数据。

#### 2、非阻塞式IO模型(non-blocking I/O)

![非阻塞式IO模型.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/e83adfbe3a7dc5b1e326d19634de5f01.jpg)

进程调用recvfrom函数，如果数据没有准备好就返回错误提示，之后进程循环调用recvfrom函数，直到有数据返回。

#### 3、IO复用模型(I/O multiplexing)

![IO复用模型.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/cd51ea70599367aa6540194e11e619ed.jpg)

进程调用select,如果没有套接字变为可读，则阻塞，直到有可读套接字之后，调用recvfrom函数，返回结果。

#### 4、信号驱动式IO模型(signal-driven I/O)

![信号驱动式IO模型.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/80b9ca1682c4fcd5c64eab4c3abec1b3.jpg)

进程先注册信号驱动，之后进程不阻塞，当数据准备好后，会给进程返回信号提示，这时进程调用ecvfrom函数，返回数据。

#### 5、异步IO模型(asynchronous I/O)

![异步IO模型.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/5e2e2dcb8891a642eb31b73b0ca28e79.jpg)

由POSIX规范定义，应用程序告知内核启动某个操作，并让内核在整个操作（包括将数据从内核拷贝到应用程序的缓冲区）完成后通知应用程序。这种模型与信号驱动模型的主要区别在于：信号驱动I/O是由内核通知应用程序何时启动一个I/O操作，而异步I/O模型是由内核通知应用程序I/O操作何时完成。

### IO模型对比

![5种IO模型比较.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/9372442def5267c752f339e223177e06.jpg)

阻塞越少，理论上性能也越优。

- 阻塞式IO，每个连接要对应一个线程单独处理，浪费资源。
- 非阻塞式IO，需要不断的轮询，也耗费CPU资源。
- 信号驱动式IO,在大量IO操作时会有信号队列溢出，且对于TCP而言，通知条件过多，每一个进行判断会消耗资源。
- 异步IO,理论最优，但是目前Linux支持还不是很完善。

因此在Linux下网络编程都以IO复用模型为主。

### 参考

[理解高性能网络模型](https://www.jianshu.com/p/2965fca6bb8f)
[IO - 同步，异步，阻塞，非阻塞 （亡羊补牢篇）](https://blog.csdn.net/historyasamirror/article/details/5778378)





