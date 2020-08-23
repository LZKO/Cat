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



## 五、线程模型

参考：[深入了解Netty【五】线程模型](<https://www.cnblogs.com/clawhub/p/11967383.html>)

### 引言

不同的线程模型对程序的性能有很大的影响，Netty是建立在Reactor模型的基础上，要搞清Netty的线程模型，需要了解一目前常见线程模型的一些概念。
具体是进程还是线程，是和平台或者编程语言相关，本文为了描述方便，以线程描述。
目前存在的线程模型有：

- 传统阻塞IO服务模型
- Reactor模型
- Proactor模型

### 1、传统阻塞IO服务模型

![传统阻塞IO服务模型.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/b51853105f64991135ec39f656742c00.jpg)

采用阻塞IO模型获取输入的数据。 每个连接需要独立的完成数据的输入，业务的处理，数据返回。
当并发数大的时候，会创建大量的线程，占用系统资源，如果连接创建后，当前线程没有数据可读，会阻塞，造成线程资源浪费。

### 2、Reactor模型

IO多路复用 线程池 = Reactor模型
![Reactor.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/a9835fcfb8d942784a4cb44115f213c7.jpg)

根据Reactor的数量和处理线程的数量，Reactor模型分为三类：

- 单Reactor单线程
- 单Reactor多线程
- 主从Reactor多线程

下面分别描述。

#### 2.1、单Reactor单线程

![单Reactor单线程.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/e13c18cbd2b04756e398b77d9200ac13.jpg)
图中：

- Reactor中的select模块就是IO多路复用模型中的选择器，可以通过一个阻塞对象监听多路连接请求。
- Reactor对象通过Select监控客户端请求事件，收到事件后，通过Dispatch进行分发。
- 如果是`建立连接`事件，则用Acceptor通过Accept处理连接请求，然后创建一个Handler对象，处理连接完成后的业务处理。
- 如果不是建立连接事件，则Reactor会分发调用连接对应的Handler处理。
- Handler会处理Read-业务-Send流程。

这种模型，在客户端数量过多时，会无法支撑。因为只有一个线程，无法发挥多核CPU性能，且Handler处理某个连接的业务时，服务端无法处理其他连接事件。
以前在学习Redis原理的时候，发现它内部就是这种模型：[深入了解Redis【十二】Reactor事件模型在Redis中的应用](https://www.clawhub.club/posts/2019/10/16/%E6%B7%B1%E5%85%A5%E4%BA%86%E8%A7%A3Redis/%E6%B7%B1%E5%85%A5%E4%BA%86%E8%A7%A3Redis%E3%80%90%E5%8D%81%E4%BA%8C%E3%80%91Reactor%E4%BA%8B%E4%BB%B6%E6%A8%A1%E5%9E%8B%E5%9C%A8Redis%E4%B8%AD%E7%9A%84%E5%BA%94%E7%94%A8/)

#### 2.2、单Reactor多线程

![单Reactor多线程.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/9075a333b3670163b27afb62ef0abee5.jpg)

图中多线程体现在两个部分：

- Reactor主线程
  Reactor通过select监听客户请求，如果是`连接请求`事件，则由Acceptor处理连接，如果是其他请求，则由dispatch找到对应的Handler,这里的Handler只负责响应事件，读取和响应，会将具体的业务处理交由Worker线程池处理。
- Worker线程池
  Worker线程池会分配独立线程完成真正的业务，并将结果返回给Handler，Handler收到响应后，通过send将结果返回给客户端。

这里Reactor处理所有的事件监听和响应，高并发情景下容易出现性能瓶颈。

#### 2.3、主从Reactor多线程

![主从Reactor多线程.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/68e487adc89b4654daad317a9dc4d57c.jpg)

这种模式是对单Reactor的改进，由原来单Reactor改成了Reactor主线程与Reactor子线程。

- Reactor主线程的MainReactor对象通过select监听`连接事件`，收到事件后，通过Acceptor处理连接事件。
- 当Acceptor处理完连接事件之后，MainReactor将连接分配给SubReactor。
- SubReactor将连接加入到连接队列进行监听，并创建handler进行事件处理。
- 当有新的事件发生时，SubReactor就会调用对应的handler处理。
- handler通过read读取数据，交由Worker线程池处理业务。
- Worker线程池分配线程处理完数据后，将结果返回给handler。
- handler收到返回的数据后，通过send将结果返回给客户端。
- MainReactor可以对应多个SubReactor。

这种优点多多，各个模块各司其职，缺点就是实现复杂。

### 3、Proactor模型

![Proactor.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/9d48bda0415043dd9a1cd6822793085a.jpg)

Proactor模型在理论上是比Reactor模型性能更好，但是因为依赖于操作系统的非阻塞异步模型，而linux的非阻塞异步模型还不完善，所以还是以Reactor为主。

### 总结

在学习这一部分知识的时候，想到redis中Reactor的应用，又想到了以前分析Tomcat源码时，其内部就是这种Reactor的思想。
突然感觉被我发现了一个天大的秘密：技术原理是通用的！

### 参考

[Netty 系列之 Netty 线程模型](https://www.infoq.cn/article/netty-threading-model)
[理解高性能网络模型](https://www.jianshu.com/p/2965fca6bb8f)

## 六、Netty工作原理

参考：[深入了解Netty【六】Netty工作原理](<https://www.cnblogs.com/clawhub/p/11967401.html>)

### 引言

前面学习了NIO与零拷贝、IO多路复用模型、Reactor主从模型。
服务器基于IO模型管理连接，获取输入数据，又基于线程模型，处理请求。
下面来学习Netty的具体应用。

### 1、Netty线程模型

Netty线程模型是建立在Reactor主从模式的基础上，主从 Rreactor 多线程模型：
![主从 Rreactor 多线程模型.jpg](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/091d0fd3d79cd4fe242894e099120616.jpg)

但是在Netty中，bossGroup相当于mainReactor，workerGroup相当于SubReactor与Worker线程池的合体。如：

复制代码

```java
EventLoopGroup bossGroup = new NioEventLoopGroup();
EventLoopGroup workerGroup = new NioEventLoopGroup();
ServerBootstrap server = new ServerBootstrap();
server.group(bossGroup, workerGroup)
      .channel(NioServerSocketChannel.class);
```

- bossGroup
  bossGroup线程池负责监听端口，获取一个线程作为MainReactor,用于处理端口的Accept事件。
- workerGroup
  workerGroup线程池负责处理Channel（通道）的I/O事件，并处理相应的业务。

在启动时，可以初始化多个线程。

复制代码

```java
EventLoopGroup bossGroup = new NioEventLoopGroup(2);
EventLoopGroup workerGroup = new NioEventLoopGroup(3);
```

### 2、Netty示例（客户端、服务器）

略

### 3、Netty工作原理

![服务端 Netty Reactor 工作架构图.jpg](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/c3f03e2f32eb22bab042ec4a8c8291a2.jpg)

服务端包含了1个boss NioEventLoopGroup和1个work NioEventLoopGroup。
NioEventLoopGroup相当于1个事件循环组，组内包含多个事件循环（NioEventLoop），每个NioEventLoop包含1个Selector和1个事件循环线程。

#### 3.1、boss NioEventLoop循环任务

- 轮询Accept事件。
- 处理Accept IO事件，与Client建立连接，生成NioSocketChannel,并将NioSocketChannel注册到某个work NioEventLoop的Selector上。
- 处理任务队列中的任务。

#### 3.2、work NioEventLoop循环任务

- 轮询Read、Write事件。
- 处理IO事件，在NioSocketChannel可读、可写事件发生时进行处理。
- 处理任务队列中的任务。

#### 3.3、任务队列中的任务

1. 用户程序自定义的普通任务

复制代码

```java
ctx.channel().eventLoop().execute(new Runnable() {
   @Override
   public void run() {
       //...
   }
});
```

1. 非当前 Reactor 线程调用 Channel 的各种方法
   例如在推送系统的业务线程里面，根据用户的标识，找到对应的 Channel 引用，然后调用 Write 类方法向该用户推送消息，就会进入到这种场景。最终的 Write 会提交到任务队列中后被异步消费。
2. 用户自定义定时任务

复制代码

```java
ctx.channel().eventLoop().schedule(new Runnable() {
   @Override
   public void run() {
       //...
   }
}, 60, TimeUnit.SECONDS);
```

### 参考

[这可能是目前最透彻的Netty原理架构解析](https://juejin.im/post/5be00763e51d453d4a5cf289)
[Netty 实战精髓篇](https://www.w3cschool.cn/essential_netty_in_action/)
[Netty入门教程](https://www.jianshu.com/p/b9f3f6a16911) 
[Essential Netty in Action](https://legacy.gitbook.com/book/waylau/essential-netty-in-action/details)



## 七、Netty核心组件

参考：[深入了解Netty【七】Netty核心组件](https://www.cnblogs.com/clawhub/p/11968113.html)

### 1、Bootstrap与ServerBootstrap

bootstrap用于引导Netty的启动，Bootstrap是客户端的引导类，ServerBootstrap是服务端引导类。类继承关系：
![bootstrap.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/31a518070c912a4eac247dd283f30af7.jpg)

### 2、Future与ChannelFuture

Netty中的操作都是异步的，等待完成或者注册监听。如：

复制代码

```java
//b为ServerBootstrap实例
 ChannelFuture f = b.bind().sync();
```

### 3、Channel

- Netty网络通信的组件，用于网络IO操作。
- 通过Channel可以获得当前网络连接的通道的状态与网络配置参数。
- Channel提供异步的网络IO操作，调用后立即返回ChannelFuture，通过注册监听，或者同步等待，最终获取结果。

Channel根据不同的协议、不同的阻塞类型，分为不同的Channel类型：
![channel.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/45aedb41ab7f44c60a901d5a6a2a2472.jpg)

通过名称也能大概猜出来其分别的作用。

### 4、Selector

Netty基于java.nio.channels.Selector对象实现IO多路复用，通过Selector一个线程可以监听多个连接的Channel事件。当向一个Selector中注册Channel后，Selector内部的机制就可以自动不断的Select这些注册的Channel是否有就绪的IO事件（可读、可写、网络连接完成等）。

### 5、ChannelHandler

ChannelHandler属于业务的核心接口，用于处理IO事件或者拦截IO操作，并将其转发到ChannelPipeline（业务处理链）中的下一个处理程序。
贴个实现类关系图：

![ChannelHanlder.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/b0dc6b996bf289e62cb79bccdcca7769.jpg)

### 6、Pipeline与ChannelPipeline

- ChannelPipeline是一个handler的集合，它负责处理和拦截出站和入站的事件和操作。
- ChannelPipeline实现了拦截过滤器模式，使用户能控制事件的处理方式。
- 在Netty中，每个Channel都有且只有一个ChannelPipeline与之对应。

一个 Channel 包含了一个 ChannelPipeline，而 ChannelPipeline 中又维护了一个由 ChannelHandlerContext 组成的双向链表，并且每个 ChannelHandlerContext 中又关联着一个 ChannelHandler。
![channelpipeline.jpg](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/060b6337573afd1d8ba11b8c73b0712b.jpg)

### 7、ChannelHandlerContext

保存Channel相关的所有上下文信息，同时关联一个ChannelHandler。

### 8、ChannelOption

Netty创建Channel实例后，可以通过ChannelOption设置参数。

### 9、NioEventLoop与NioEventLoopGroup

NioEventLoopGroup可以理解为线程池，NioEventLoop理解为一个线程，每个EventLoop对应一个Selector，负责处理多个Channel上的事件。
![ServerwithtwoEventLoopGroups.jpg](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/94bd18906d66cc80da667bd1ebc16843.jpg)

第一个boss EventLoopGroup分配一个EventLoop负责创建Channels传入的连接请求。一旦连接接受，第二个work EventLoopGroup分配一个 EventLoop给它的Channel。



## 八、TCP拆包、粘包和解决方案

参考：[深入了解Netty【八】TCP拆包、粘包和解决方案](https://www.cnblogs.com/clawhub/p/11973298.html)

### 1、TCP协议传输过程

TCP协议是面向流的协议，是流式的，没有业务上的分段，只会根据当前套接字缓冲区的情况进行拆包或者粘包：
![TCP协议传输过程.jpg](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/eb69f4c81cbd374a6821c97c21ad52f1.jpg)

发送端的字节流都会先传入缓冲区，再通过网络传入到接收端的缓冲区中，最终由接收端获取。

### 2、TCP粘包和拆包概念

因为TCP会根据缓冲区的实际情况进行包的划分，在业务上认为，有的包被拆分成多个包进行发送，也可能多个晓小的包封装成一个大的包发送，这就是TCP的粘包或者拆包。

### 3、TCP粘包和拆包图解

![粘包拆包图解.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/6b36e4382bb4fd63b45c344e6bcf0a13.jpg)

假设客户端分别发送了两个数据包D1和D2给服务端，由于服务端一次读取到字节数是不确定的，故可能存在以下几种情况：

1. 服务端分两次读取到两个独立的数据包，分别是D1和D2，没有粘包和拆包。
2. 服务端一次接收到了两个数据包，D1和D2粘在一起，发生粘包。
3. 服务端分两次读取到数据包，第一次读取到了完整的D1包和D2包的部分内容，第二次读取到了D2包的剩余内容，发生拆包。
4. 服务端分两次读取到数据包，第一次读取到部分D1包，第二次读取到剩余的D1包和全部的D2包。

当TCP缓存再小一点的话，会把D1和D2分别拆成多个包发送。

### 4、TCP粘包和拆包解决策略

因为TCP只负责数据发送，并不处理业务上的数据，所以只能在上层应用协议栈解决，目前的解决方案归纳：

1. 消息定长，每个报文的大小固定，如果数据不够，空位补空格。
2. 在包的尾部加回车换行符标识。
3. 将消息分为消息头与消息体，消息头中包含消息总长度。
4. 设计更复杂的协议。

### 5、Netty中的解决办法

Netty提供了多种默认的编码器解决粘包和拆包：
![Netty解决方案.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/1efe78da636bc0c9c7df3739288b4d97.jpg)

#### 5.1、LineBasedFrameDecoder

基于回车换行符的解码器，当遇到"\n"或者 "\r\n"结束符时，分为一组。支持携带结束符或者不带结束符两种编码方式，也支持配置单行的最大长度。
LineBasedFrameDecoder与StringDecoder搭配时，相当于按行切换的文本解析器，用来支持TCP的粘包和拆包。
使用例子：

复制代码

```java
private void start() throws Exception {
        //创建 EventLoopGroup
        NioEventLoopGroup group = new NioEventLoopGroup();
        NioEventLoopGroup work = new NioEventLoopGroup();
        try {
            //创建 ServerBootstrap
            ServerBootstrap b = new ServerBootstrap();
            b.group(group, work)
                    //指定使用 NIO 的传输 Channel
                    .channel(NioServerSocketChannel.class)
                    //设置 socket 地址使用所选的端口
                    .localAddress(new InetSocketAddress(port))
                    //添加 EchoServerHandler 到 Channel 的 ChannelPipeline
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new LineBasedFrameDecoder(1024));
                            p.addLast(new StringDecoder());
                            p.addLast(new StringEncoder());
                            p.addLast(new EchoServerHandler());
                        }
                    });
            //绑定的服务器;sync 等待服务器关闭
            ChannelFuture f = b.bind().sync();
            System.out.println(EchoServer.class.getName()   " started and listen on "   f.channel().localAddress());
            //关闭 channel 和 块，直到它被关闭
            f.channel().closeFuture().sync();
        } finally {
            //关机的 EventLoopGroup，释放所有资源。
            group.shutdownGracefully().sync();
        }
    }
```

注意ChannelPipeline 中ChannelHandler的顺序，

#### 5.2、DelimiterBasedFrameDecoder

分隔符解码器，可以指定消息结束的分隔符，它可以自动完成以分隔符作为码流结束标识的消息的解码。回车换行解码器实际上是一种特殊的DelimiterBasedFrameDecoder解码器。
使用例子（后面的代码只贴ChannelPipeline部分）：

复制代码

```java
ChannelPipeline p = ch.pipeline();
p.addLast(new DelimiterBasedFrameDecoder(1024, Unpooled.copiedBuffer("制定的分隔符".getBytes())));
p.addLast(new StringDecoder());
p.addLast(new StringEncoder());
p.addLast(new EchoServerHandler());
```

#### 5.3、FixedLengthFrameDecoder

固定长度解码器，它能够按照指定的长度对消息进行自动解码,当制定的长度过大，消息过短时会有资源浪费，但是使用起来简单。

复制代码

```java
 ChannelPipeline p = ch.pipeline();
p.addLast(new FixedLengthFrameDecoder(1 << 5));
p.addLast(new StringDecoder());
p.addLast(new StringEncoder());
p.addLast(new EchoServerHandler());
```

#### 5.4、LengthFieldBasedFrameDecoder

通用解码器，一般协议头中带有长度字段，通过使用LengthFieldBasedFrameDecoder传入特定的参数，来解决拆包粘包。
io.netty.handler.codec.LengthFieldBasedFrameDecoder的实例化：

复制代码

```java
    /**
     * Creates a new instance.
     *
     * @param maxFrameLength      最大帧长度。也就是可以接收的数据的最大长度。如果超过，此次数据会被丢弃。
     * @param lengthFieldOffset   长度域偏移。就是说数据开始的几个字节可能不是表示数据长度，需要后移几个字节才是长度域。
     * @param lengthFieldLength   长度域字节数。用几个字节来表示数据长度。
     * @param lengthAdjustment    数据长度修正。因为长度域指定的长度可以是header body的整个长度，也可以只是body的长度。如果表示header body的整个长度，那么我们需要修正数据长度。
     * @param initialBytesToStrip 跳过的字节数。如果你需要接收header body的所有数据，此值就是0，如果你只想接收body数据，那么需要跳过header所占用的字节数。
     * @param failFast            如果为true，则在解码器注意到帧的长度将超过maxFrameLength时立即抛出TooLongFrameException，而不管是否已读取整个帧。
     *                            如果为false，则在读取了超过maxFrameLength的整个帧之后引发TooLongFrameException。
     */
    public LengthFieldBasedFrameDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
                                        int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        //略
    }
```

- maxFrameLength
  最大帧长度。也就是可以接收的数据的最大长度。如果超过，此次数据会被丢弃。
- lengthFieldOffset
  长度域偏移。就是说数据开始的几个字节可能不是表示数据长度，需要后移几个字节才是长度域。
- lengthFieldLength
  长度域字节数。用几个字节来表示数据长度。
- lengthAdjustment
  数据长度修正。因为长度域指定的长度可以是header body的整个长度，也可以只是body的长度。如果表示header body的整个长度，那么我们需要修正数据长度。
- initialBytesToStrip
  跳过的字节数。如果你需要接收header body的所有数据，此值就是0，如果你只想接收body数据，那么需要跳过header所占用的字节数。
- failFast
  如果为true，则在解码器注意到帧的长度将超过maxFrameLength时立即抛出TooLongFrameException，而不管是否已读取整个帧。
  如果为false，则在读取了超过maxFrameLength的整个帧之后引发TooLongFrameException。

下面通过Netty源码中LengthFieldBasedFrameDecoder的注释几个例子看一下参数的使用：

##### 5.4.1、2 bytes length field at offset 0, do not strip header

本例中的length字段的值是12 (0x0C)，它表示“HELLO, WORLD”的长度。默认情况下，解码器假定长度字段表示长度字段后面的字节数。

- lengthFieldOffset = 0： 开始的2个字节就是长度域，所以不需要长度域偏移。
- lengthFieldLength = 2： 长度域2个字节。
- lengthAdjustment = 0： 数据长度修正为0，因为长度域只包含数据的长度，所以不需要修正。
- initialBytesToStrip = 0： 发送和接收的数据完全一致，所以不需要跳过任何字节。

![LengthFieldBasedFrameDecoder-1.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/0641c94e694c866de7ecabec7c1970e6.jpg)

##### 5.4.2、2 bytes length field at offset 0, strip header

因为我们可以通过调用readableBytes()来获得内容的长度，所以可能希望通过指定initialbystrip来删除长度字段。在本例中，我们指定2(与length字段的长度相同)来去掉前两个字节。

- lengthFieldOffset = 0： 开始的2个字节就是长度域，所以不需要长度域偏移。
- lengthFieldLength = 2 ：长度域2个字节。
- lengthAdjustment = 0： 数据长度修正为0，因为长度域只包含数据的长度，所以不需要修正。
- initialBytesToStrip = 2 ：我们发现接收的数据没有长度域的数据，所以要跳过长度域的2个字节。

![LengthFieldBasedFrameDecoder-2.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/ba86cb313c2ba2fc7bc8bea61215ae74.jpg)

##### 5.4.3、2 bytes length field at offset 0, do not strip header, the length field represents the length of the whole message

在大多数情况下，length字段仅表示消息体的长度，如前面的示例所示。但是，在一些协议中，长度字段表示整个消息的长度，包括消息头。在这种情况下，我们指定一个非零长度调整。因为这个示例消息中的长度值总是比主体长度大2，所以我们指定-2作为补偿的长度调整。

- lengthFieldOffset = 0： 开始的2个字节就是长度域，所以不需要长度域偏移。
- lengthFieldLength = 2： 长度域2个字节。
- lengthAdjustment = -2 ：因为长度域为总长度，所以我们需要修正数据长度，也就是减去2。
- initialBytesToStrip = 0 ：发送和接收的数据完全一致，所以不需要跳过任何字节。

![LengthFieldBasedFrameDecoder-3.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/9d0ce5a6cd5fe8b5631137f5315fe538.jpg)

##### 5.4.4、3 bytes length field at the end of 5 bytes header, do not strip header

下面的消息是第一个示例的简单变体。一个额外的头值被预先写入消息中。长度调整再次为零，因为译码器在计算帧长时总是考虑到预写数据的长度。

- lengthFieldOffset = 2 ：(= the length of Header 1)跳过2字节之后才是长度域
- lengthFieldLength = 3：长度域3个字节。
- lengthAdjustment = 0：数据长度修正为0，因为长度域只包含数据的长度，所以不需要修正。
- initialBytesToStrip = 0：发送和接收的数据完全一致，所以不需要跳过任何字节。

![LengthFieldBasedFrameDecoder-4.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/0f0d667be7bbd2eb80191770aaf329d7.jpg)

##### 5.4.5、3 bytes length field at the beginning of 5 bytes header, do not strip header

这是一个高级示例，展示了在长度字段和消息正文之间有一个额外头的情况。您必须指定一个正的长度调整，以便解码器将额外的标头计数到帧长度计算中。

- lengthFieldOffset = 0：开始的就是长度域，所以不需要长度域偏移。
- lengthFieldLength = 3：长度域3个字节。
- lengthAdjustment = 2 ：(= the length of Header 1) 长度修正2个字节，加2
- initialBytesToStrip = 0：发送和接收的数据完全一致，所以不需要跳过任何字节。

![LengthFieldBasedFrameDecoder-5.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/1d7de5a480e2fb2c948070c2e579cd1d.jpg)

##### 5.4.6、2 bytes length field at offset 1 in the middle of 4 bytes header, strip the first header field and the length field

这是上述所有示例的组合。在长度字段之前有预写的header，在长度字段之后有额外的header。预先设置的header会影响lengthFieldOffset，而额外的leader会影响lengthAdjustment。我们还指定了一个非零initialBytesToStrip来从帧中去除长度字段和预定的header。如果不想去掉预写的header，可以为initialBytesToSkip指定0。

- lengthFieldOffset = 1 ：(= the length of HDR1) ，跳过1个字节之后才是长度域
- lengthFieldLength = 2：长度域2个字节
- lengthAdjustment = 1： (= the length of HDR2)
- initialBytesToStrip = 3 ：(= the length of HDR1 LEN)

![LengthFieldBasedFrameDecoder-6.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/9308a228442c26f30151e445646eb7e6.jpg)

##### 5.4.7、2 bytes length field at offset 1 in the middle of 4 bytes header, strip the first header field and the length field, the length field represents the length of the whole message

让我们对前面的示例进行另一个修改。与前一个示例的惟一区别是，length字段表示整个消息的长度，而不是消息正文的长度，就像第三个示例一样。我们必须把HDR1的长度和长度计算进长度调整里。请注意，我们不需要考虑HDR2的长度，因为length字段已经包含了整个头的长度。

- lengthFieldOffset = 1：长度域偏移1个字节，之后才是长度域。
- lengthFieldLength = 2：长度域2个字节。
- lengthAdjustment = -3： (= the length of HDR1 LEN, negative)数据长度修正-3个字节。
- initialBytesToStrip = 3：因为接受的数据比发送的数据少3个字节，所以跳过3个字节。

![LengthFieldBasedFrameDecoder-7.png](https://cdn.jsdelivr.net/gh/clawhub/image/diffuser/blog/19/11/29/ee17463878a745415c0af3902db5905f.jpg)



