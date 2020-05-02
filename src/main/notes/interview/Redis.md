## Redis

[参考](<https://github.com/CyC2018/CS-Notes/blob/master/notes/Redis.md>)

------

<!-- GFM-TOC -->

- [一、概述](#一、概述)
- [二、数据类型](#二、数据类型)
  - [STRING](#string)
  - [LIST](#list)
  - [SET](#set)
  - [HASH](#hash)
  - [ZSET](#zset)
- [三、数据结构](#三、数据结构)
  - [简单动态字符串（SDS）](#简单动态字符串（SDS）)
  - [链表](#链表)
  - [字典](#字典)
  - [跳跃表](#跳跃表)
- [四、使用场景](#四、使用场景)
  - [计数器](#计数器)
  - [缓存](#缓存)
  - [查找表](#查找表)
  - [消息队列](#消息队列)
  - [会话缓存](#会话缓存)
  - [分布式锁实现](#分布式锁实现)
  - [其它](#其它)
- [五、Redis 与 Memcached](#五、redis-与-memcached)
  - [数据类型](#数据类型)
  - [数据持久化](#数据持久化)
  - [分布式](#分布式)
  - [内存管理机制](#内存管理机制)
- [六、键的过期时间](#六、键的过期时间)
- [七、数据淘汰策略](#七、数据淘汰策略)
- [八、持久化](#、八持久化)
  - [RDB 持久化](#rdb-持久化)
  - [AOF 持久化](#aof-持久化)
- [九、事务](#九、事务)
- [十、事件](#十、事件)
  - [文件事件](#文件事件)
  - [时间事件](#时间事件)
  - [事件的调度与执行](#事件的调度与执行)
- [十一、复制](#十一、复制)
  - [连接过程](#连接过程)
  - [主从链](#主从链)
- [十二、Sentinel](#十二、sentinel)
- [十三、分片](#十三、分片)
- [十四、一个简单的论坛系统分析](#十四、一个简单的论坛系统分析)
  - [文章信息](#文章信息)
  - [点赞功能](#点赞功能)
  - [对文章进行排序](#对文章进行排序)
- [参考资料](#参考资料)
  <!-- GFM-TOC -->



### 一、概述

Redis 是速度非常快的非关系型（NoSQL）内存键值数据库，可以存储键和五种不同类型的值之间的映射。

键的类型只能为字符串，值支持五种数据类型：字符串、列表、集合、散列表、有序集合。

Redis 支持很多特性，例如将内存中的数据持久化到硬盘中，使用复制来扩展读性能，使用分片来扩展写性能。

### 二、数据类型

#### 

| 数据类型 | 可以存储的值           | 操作                                                         |
| -------- | ---------------------- | ------------------------------------------------------------ |
| STRING   | 字符串、整数或者浮点数 | 对整个字符串或者字符串的其中一部分执行操作 对整数和浮点数执行自增或者自减操作 |
| LIST     | 列表                   | 从两端压入或者弹出元素 对单个或者多个元素进行修剪， 只保留一个范围内的元素 |
| SET      | 无序集合               | 添加、获取、移除单个元素 检查一个元素是否存在于集合中 计算交集、并集、差集 从集合里面随机获取元素 |
| HASH     | 包含键值对的无序散列表 | 添加、获取、移除单个键值对 获取所有键值对 检查某个键是否存在 |
| ZSET     | 有序集合               | 添加、获取、删除元素 根据分值范围或者成员来获取元素 计算一个键的排名 |

> [What Redis data structures look like](https://redislabs.com/ebook/part-1-getting-started/chapter-1-getting-to-know-redis/1-2-what-redis-data-structures-look-like/)

#### STRING

```
> set hello world
OK
> get hello
"world"
> del hello
(integer) 1
> get hello
(nil)
```

#### LIST

```
> rpush list-key item
(integer) 1
> rpush list-key item2
(integer) 2
> rpush list-key item
(integer) 3

> lrange list-key 0 -1
1) "item"
2) "item2"
3) "item"

> lindex list-key 1
"item2"

> lpop list-key
"item"

> lrange list-key 0 -1
1) "item2"
2) "item"
```

#### SET

```
> sadd set-key item
(integer) 1
> sadd set-key item2
(integer) 1
> sadd set-key item3
(integer) 1
> sadd set-key item
(integer) 0

> smembers set-key
1) "item"
2) "item2"
3) "item3"

> sismember set-key item4
(integer) 0
> sismember set-key item
(integer) 1

> srem set-key item2
(integer) 1
> srem set-key item2
(integer) 0

> smembers set-key
1) "item"
2) "item3"
```

#### HASH

```
> hset hash-key sub-key1 value1
(integer) 1
> hset hash-key sub-key2 value2
(integer) 1
> hset hash-key sub-key1 value1
(integer) 0

> hgetall hash-key
1) "sub-key1"
2) "value1"
3) "sub-key2"
4) "value2"

> hdel hash-key sub-key2
(integer) 1
> hdel hash-key sub-key2
(integer) 0

> hget hash-key sub-key1
"value1"

> hgetall hash-key
1) "sub-key1"
2) "value1"
```

#### ZSET

```
> zadd zset-key 728 member1
(integer) 1
> zadd zset-key 982 member0
(integer) 1
> zadd zset-key 982 member0
(integer) 0

> zrange zset-key 0 -1 withscores
1) "member1"
2) "728"
3) "member0"
4) "982"

> zrangebyscore zset-key 0 800 withscores
1) "member1"
2) "728"

> zrem zset-key member1
(integer) 1
> zrem zset-key member1
(integer) 0

> zrange zset-key 0 -1 withscores
1) "member0"
2) "982"
```

### 三、数据结构

#### 简单动态字符串（SDS）

Redis里，C字符串只会作为字符串字面量用在一些无需对字符串值进行修改的地方，比如打印日志。Redis构建了 简单动态字符串（simple dynamic string，SDS）来表示字符串值。

在Redis里，包含字符串值的键值对在底层都是由SDS实现的。除此之外，SDS还被用作缓冲区：AOF缓冲区，客户端状态中的输入缓冲区。

##### 1.SDS的定义

每个sds.h/sdshdr结构表示一个SDS值：

```
struct sdshdr {
  // 记录buf数组中已使用字节的数量
  // 等于SDS所保存字符串的长度
  int len;
  
  // 记录buf数组中未使用字节的数量
  int free;
  
  // 字节数组，用于保存字符串
  char buf[];
}
```

SDS遵循C字符串以空字符结尾的管理，空字符不计算在len属性中。这样，SDS可以重用一部分C字符串函数库，如printf。

##### 2.SDS与C字符串的区别

- 常熟复杂度获取字符串长度

  C字符串必须遍历整个字符串才能获得长度，复杂度是O(N)。

  SDS在len属性中记录了SDS的长度，复杂度为O(1)。

- 杜绝缓冲区溢出

  C字符串不记录长度的带来的另一个问题是缓冲区溢出。假设s1和s2是紧邻的两个字符串，对s1的strcat操作，有可能污染s2的内存空间。

  SDS的空间分配策略杜绝了缓冲区溢出的可能性：但SDS API修改SDS时，会先检查SDS的空间是否满足修改所需的要求，不满足的话，API会将SDS的空间扩展至执行修改所需的大小，然后再执行实际的修改操作。

- 减少修改字符串时带来的内存重分配次数

  每次增长或缩短一个C字符串，程序都要对保存这个C字符串的数组进行一次内存重分配操作。

  Redis作为数据库，数据会被频繁修改，如果每次修改字符串都会执行一次内存重分配的话，会对性能造成影响。SDS通过未使用空间解除了字符串长度和底层数组长度之间的关联：在SDS中，buf数组的长度不一定就是字符数量加一，数组里面可以包含未使用的字节，由free属性记录。

  对于未使用空间，SDS使用了空间预分配和惰性空间释放两种优化策略：

  1. 空间预分配：当SDS的API对SDS修改并需要空间扩展时，程序不仅为SDS分配修改所需的空间，还会分配额外的未使用空间（分配的大小取决于长度是否小于1MB）。
  2. 惰性空间释放：当SDS的API需要缩短时，程序不立即触发内存重分配，而是使用free属性将这些字节的数量记录下来，并等待将来使用。与此同时，SDS API也可以让我们真正释放未使用空间，防止内存浪费。

- 二进制安全

  C字符串中的字符必须复合某种编码（如ASCII），且除了字符串末尾之外，字符串里不能包含空字符。这些限制使得C字符串只能保存文本，而不能保存像图片、音频、视频、压缩文件这样的二进制数据。

  SDS API会以处理二进制的方式处理SDS存放在buf数组中的数据，写入时什么样，读取时就是什么样。

  这也是我们将SDS 的buf 属性称为字节数组的原因——Redis 不是用这个数组来保存字符，而是用它来保存一系列二进制数据。

- 兼容部分C 字符串函数

  遵循C字符串以空字符结尾的管理，SDS可以重用<string.h>函数库。

总结：

| C字符串                          | SDS                                |
| -------------------------------- | ---------------------------------- |
| 获取字符串长度的复杂度O(N)       | O(1)                               |
| API不安全，可能会造成缓冲区溢出  | API安全，不会造成缓冲区溢出        |
| 修改字符串长度必然导致内存重分配 | 修改字符串长度不一定导致内存重分配 |
| 只能保存文本数据                 | 可以保存文本或二进制数据           |
| 可使用所有<string.h>库的函数     | 可使用部分<string.h>库的函数       |

##### 3.SDS API

略

#### 链表

Redis构建了自己的链表实现。列表键的底层实现之一就是链表。发布、订阅、慢查询、监视器都用到了链表。Redis服务器还用链表保存多个客户端的状态信息，以及构建客户端输出缓冲区。

##### 1.链表和链表节点的实现

链表节点用adlist.h/listNode结构来表示

```
typedef struct listNode {
  struct listNode *prev;
  struct listNode *next; 
  void *value;
} listNode;
```

adlist.h/list来持有链表:

```
typedef struct list {
  listNode *head;
  listNode *tail;
  unsigned long len;
  void *(dup)(void *ptr); // 节点复制函数
  void (*free)(void *ptr); // 节点释放函数
  int (*match)(void *ptr, void *key); // 节点值对比函数
} list;
```

Redis的链表实现可总结如下：

1. 双向
2. 无环。表头结点的prev和表尾节点的next都指向NULL
3. 带表头指针和表尾指针
4. 带链表长度计数器
5. 多态。使用void*指针来保存节点值，并通过list结构的dup、free。match三个属性为节点值设置类型特定函数

##### 2.链表和链表节点的API

略



#### 字典

Redis的数据库就是使用字典来作为底层实现的，对数据库的增删改查都是构建在字典的操作之上。

字典还是哈希键的底层实现之一，但一个哈希键包含的键值对比较多，又或者键值对中的元素都是较长的字符串时，Redis就会用字典作为哈希键的底层实现。

##### 1.字典的实现

Redis的字典使用**哈希表**作为底层实现，每个哈希表节点就保存了字典中的一个键值对。

Redis字典所用的**哈希表**由dict.h/dictht结构定义：

```
typedef struct dictht {
  // 哈希表数组
  dict Entry **table;
  // 哈希表大小
  unsigned long size;
  // 哈希表大小掩码，用于计算索引值，总是等于size - 1
  unsigned long sizemask;
  // 该哈希表已有节点的数量
  unsigned long used;
} dictht;
```

**哈希表节点**使用dictEntry结构表示，每个dictEntry结构都保存着一个键值对：

```
typedef struct dictEntry {
  void *key; // 键
  
  // 值
  union {
    void *val;
    uint64_t u64;
    int64_t s64;
  } v;
  
  // 指向下个哈希表节点，形成链表。一次解决键冲突的问题
  struct dictEntry *next;
}
```

Redis中的**字典**由dict.h/dict结构表示：

```
typedef struct dict {
  dictType *type; // 类型特定函数
  void *privdata; // 私有数据
  
  /*
  哈希表
  一般情况下，字典只是用ht[0]哈希表，ht[1]只会在对ht[0]哈希表进行rehash时是用
  */
  dictht ht[2]; 
  
  // rehash索引，但rehash不在进行时，值为-1
  // 记录了rehash的进度
  int trehashidx; 
} dict;
```

type和privdata是针对不同类型大家键值对，为创建多态字典而设置的：

- type是一个指向dictType结构的指针，每个dictType都保存了一簇用于操作特定类型键值对的函数，Redis会为用途不同的字典设置不同的类型特定函数。
- privdata保存了需要传给那些类型特定函数的可选参数。

```
typedef struct dictType {
  // 计算哈希值的函数
  unsigned int (*hashFunction) (const void *key);
  
  // 复制键的函数
  void *(*keyDup) (void *privdata, const void *obj);
  
  // 对比键的函数
  void *(*keyCompare) (void *privdata, const void *key1, const void *key2);
  
  // 销毁键的函数
  void (*keyDestructor) (void *privdata, void *key);
  
  // 销毁值的函数
  void (*valDestructor) (void *privdata, void *obj);
} dictType;
```

##### 2.哈希算法

Redis计算哈希值和索引值的方法如下：

```
# 使用字典设置的哈希函数，计算key的哈希值
hash = dict.type.hashFucntion(key)
# 使用哈希表的sizemask属性和哈希值，计算出索引值
# 根据情况的不同，ht[x]可以使ht[0]或ht[1]
index = hash & dict.ht[x].sizemask
```

当字典被用作数据库或哈希键的底层实现时，使用MurmurHash2算法来计算哈希值，即使输入的键是有规律的，算法人能有一个很好的随机分布性，计算速度也很快。

##### 3.解决键冲突

Redis使用链地址法解决键冲突，每个哈希表节点都有个next指针。

##### 4.rehash

随着操作的不断执行，哈希表保存的键值对会增加或减少。为了让哈希表的负载因子维持在合理范围，需要对哈希表的大小进行扩展或收缩，即通过执行rehash（重新散列）来完成：

1. 为字典的ht[1]哈希表分配空间：

   如果执行的是扩展操作，ht[1]的大小为第一个大于等于ht[0].used * 2 的2^n

   如果执行的是收缩操作，ht[1]的大小为第一个大于等于ht[0].used的2^n

2. 将保存在ht[0]中的所有键值对rehash到ht[1]上。rehash是重新设计的计算键的哈希值和索引值

3. 释放ht[0]，将ht[1]设置为ht[0]，并为ht[1]新建一个空白哈希表

**哈希表的扩展与收缩**

满足一下任一条件，程序会自动对哈希表执行扩展操作：

1. 服务器目前没有执行BGSAVE或BGREWRITEAOF，且哈希表负载因子大于等于1
2. 服务器正在执行BGSAVE或BGREWRITEAOF，且负载因子大于5

其中负载因子的计算公式：

```
# 负载因子 = 哈希表已保存节点数量 / 哈希表大小
load_factor = ht[0].used / ht[0].size
```

注：执行BGSAVE或BGREWRITEAOF过程中，Redis需要创建当前服务器进程的子进程，而多数操作系统都是用写时复制来优化子进程的效率，所以在子进程存在期间，服务器会提高执行扩展操作所需的负载因子，从而尽可能地避免在子进程存在期间扩展哈希表，避免不避免的内存写入，节约内存。

##### 5.渐进式rehash

将ht[0]中的键值对rehash到ht[1]中的操作不是一次性完成的，而是分多次渐进式的：

1. 为ht[1]分配空间
2. 在字典中维持一个索引计数器变量rehashidx，设置为0，表示rehash工作正式开始
3. rehash期间，**每次对字典的增删改查操作**，会顺带将ht[0]在rehashidx索引上的所有键值对rehash到ht[1]，rehash完成之后，rehashidx属性的值+1
4. 最终ht[0]会全部rehash到ht[1]，这是将rehashidx设置为-1，表示rehash完成

渐进式rehash过程中，字典会有两个哈希表，字典的增删改查会在两个哈希表上进行。

##### 6.字典API

略



如下为旧版本

dictht 是一个散列表结构，使用拉链法解决哈希冲突。

Redis 的字典 dict 中包含两个哈希表 dictht，这是为了方便进行 rehash 操作。在扩容时，将其中一个 dictht 上的键值对 rehash 到另一个 dictht 上面，完成之后释放空间并交换两个 dictht 的角色。

rehash 操作不是一次性完成，而是采用渐进方式，这是为了避免一次性执行过多的 rehash 操作给服务器带来过大的负担。

渐进式 rehash 通过记录 dict 的 rehashidx 完成，它从 0 开始，然后每执行一次 rehash 都会递增。例如在一次 rehash 中，要把 dict[0] rehash 到 dict[1]，这一次会把 dict[0] 上 table[rehashidx] 的键值对 rehash 到 dict[1] 上，dict[0] 的 table[rehashidx] 指向 null，并令 rehashidx++。

在 rehash 期间，每次对字典执行添加、删除、查找或者更新操作时，都会执行一次渐进式 rehash。

采用渐进式 rehash 会导致字典中的数据分散在两个 dictht 上，因此对字典的查找操作也需要到对应的 dictht 去执行。

#### 跳跃表

跳跃表是一种**有序数据结构**，它通过在每个节点中维持多个指向其他节点的指针，从而达到快速访问的目的。跳跃表支持平均*O(logN)*、最坏*O(N)*的查找，还可以通过顺序性操作来批量处理节点。

Redis使用跳跃表作为有序集合键的底层实现之一，如果有序集合包含的元素数量较多，或者有序集合中元素的成员是比较长的字符串时，Redis使用跳跃表来实现有序集合键。

在集群节点中，跳跃表也被Redis用作内部数据结构。

##### 1.跳跃表的实现

Redis的跳跃表由redis.h/zskiplistNode和redis.h/zskiplist两个结构定义，其中zskiplistNode代表跳跃表节点，zskiplist保存跳跃表节点的相关信息，比如节点数量、以及指向表头/表尾结点的指针等。

```
typedef struct zskiplist {
  struct zskiplistNode *header, *tail;
  unsigned long length;
  int leve;
} zskiplist;
```

zskiplist结构包含：

- header：指向跳跃表的表头结点
- tail：指向跳跃表的表尾节点
- level：记录跳跃表内，层数最大的那个节点的层数（表头结点不计入）
- length：记录跳跃表的长度， 即跳跃表目前包含节点的数量（表头结点不计入）

```
typedef struct zskiplistNode {
  struct zskiplistLevel {
    struct zskiplistNode *forward;
    unsigned int span; // 跨度
  } level[];
  
  struct zskiplistNode *backward;
  double score;
  robj *obj;
} zskiplistNode;
```

```
typedef struct zskiplistNode {
  struct zskiplistLevel {
    struct zskiplistNode *forward;
    unsigned int span; // 跨度
  } level[];
  
  struct zskiplistNode *backward;
  double score;
  robj *obj;
} zskiplistNode;
```

zskiplistNode包含：

- level：节点中用L1、L2、L3来标记节点的各个层，每个层都有两个属性：前进指针和跨度。前进指针用来访问表尾方向的其他节点，跨度记录了前进指针所指向节点和当前节点的距离（图中曲线上的数字）。

  level数组可以包含多个元素，每个元素都有一个指向其他节点的指针，程序可以通过这些层来加快访问其他节点。层数越多，访问速度就越快。没创建一个新节点的时候，根据幂次定律（越大的数出现的概率越小）随机生成一个介于1-32之间的值作为level数组的大小。这个大小就是层的高度。

  跨度用来计算排位（rank）：在查找某个节点的过程中，将沿途访问过的所有层的跨度累计起来，得到就是目标节点的排位。

- 后退指针：BW，指向位于当前节点的前一个节点。只能回退到前一个节点，不可跳跃。

- 分值（score）：节点中的1.0/2.0/3.0保存的分值，节点按照各自保存的分值从小到大排列。节点的分值可以相同。

- 成员对象（obj）：节点中的o1/o2/o3。它指向一个字符串对象，字符串对象保存着一个SDS值。

注：表头结点也有后退指针、分值和成员对象，只是不被用到。

遍历所有节点的路径：

1. 访问跳跃表的表头，然后从第四层的前景指正到表的第二个节点。
2. 在第二个节点时，沿着第二层的前进指针到表中的第三个节点。
3. 在第三个节点时，沿着第二层的前进指针到表中的第四个节点。
4. 但程序沿着第四个程序的前进指针移动时，遇到NULL。结束遍历。









如下为旧版本

是有序集合的底层实现之一。

跳跃表是基于多指针有序链表实现的，可以看成多个有序链表。

在查找时，从上层指针开始查找，找到对应的区间之后再到下一层去查找。下图演示了查找 22 的过程。

与红黑树等平衡树相比，跳跃表具有以下优点：

- 插入速度非常快速，因为不需要进行旋转等操作来维护平衡性；
- 更容易实现；
- 支持无锁操作。

### 四、使用场景

#### 计数器

可以对 String 进行自增自减运算，从而实现计数器功能。

Redis 这种内存型数据库的读写性能非常高，很适合存储频繁读写的计数量。

#### 缓存

将热点数据放到内存中，设置内存的最大使用量以及淘汰策略来保证缓存的命中率。

#### 查找表

例如 DNS 记录就很适合使用 Redis 进行存储。

查找表和缓存类似，也是利用了 Redis 快速的查找特性。但是查找表的内容不能失效，而缓存的内容可以失效，因为缓存不作为可靠的数据来源。

#### 消息队列

List 是一个双向链表，可以通过 lpush 和 rpop 写入和读取消息

不过最好使用 Kafka、RabbitMQ 等消息中间件。

#### 会话缓存

可以使用 Redis 来统一存储多台应用服务器的会话信息。

当应用服务器不再存储用户的会话信息，也就不再具有状态，一个用户可以请求任意一个应用服务器，从而更容易实现高可用性以及可伸缩性。

#### 分布式锁实现

在分布式场景下，无法使用单机环境下的锁来对多个节点上的进程进行同步。

可以使用 Redis 自带的 SETNX 命令实现分布式锁，除此之外，还可以使用官方提供的 RedLock 分布式锁实现。

#### 其它

Set 可以实现交集、并集等操作，从而实现共同好友等功能。

ZSet 可以实现有序性操作，从而实现排行榜等功能。

### 五、Redis 与 Memcached

两者都是非关系型内存键值数据库，主要有以下不同：

#### 数据类型

Memcached 仅支持字符串类型，而 Redis 支持五种不同的数据类型，可以更灵活地解决问题。

#### 数据持久化

Redis 支持两种持久化策略：RDB 快照和 AOF 日志，而 Memcached 不支持持久化。

#### 分布式

Memcached 不支持分布式，只能通过在客户端使用一致性哈希来实现分布式存储，这种方式在存储和查询时都需要先在客户端计算一次数据所在的节点。

Redis Cluster 实现了分布式的支持。

#### 内存管理机制

- 在 Redis 中，并不是所有数据都一直存储在内存中，可以将一些很久没用的 value 交换到磁盘，而 Memcached 的数据则会一直在内存中。
- Memcached 将内存分割成特定长度的块来存储数据，以完全解决内存碎片的问题。但是这种方式会使得内存的利用率不高，例如块的大小为 128 bytes，只存储 100 bytes 的数据，那么剩下的 28 bytes 就浪费掉了。

### 六、键的过期时间

Redis 可以为每个键设置过期时间，当键过期时，会自动删除该键。

对于散列表这种容器，只能为整个键设置过期时间（整个散列表），而不能为键里面的单个元素设置过期时间。

### 七、数据淘汰策略

可以设置内存最大使用量，当内存使用量超出时，会施行数据淘汰策略。

Redis 具体有 6 种淘汰策略：

| 策略            | 描述                                                 |
| --------------- | ---------------------------------------------------- |
| volatile-lru    | 从已设置过期时间的数据集中挑选最近最少使用的数据淘汰 |
| volatile-ttl    | 从已设置过期时间的数据集中挑选将要过期的数据淘汰     |
| volatile-random | 从已设置过期时间的数据集中任意选择数据淘汰           |
| allkeys-lru     | 从所有数据集中挑选最近最少使用的数据淘汰             |
| allkeys-random  | 从所有数据集中任意选择数据进行淘汰                   |
| noeviction      | 禁止驱逐数据                                         |

作为内存数据库，出于对性能和内存消耗的考虑，Redis 的淘汰算法实际实现上并非针对所有 key，而是抽样一小部分并且从中选出被淘汰的 key。

使用 Redis 缓存数据时，为了提高缓存命中率，需要保证缓存数据都是热点数据。可以将内存最大使用量设置为热点数据占用的内存量，然后启用 allkeys-lru 淘汰策略，将最近最少使用的数据淘汰。

Redis 4.0 引入了 volatile-lfu 和 allkeys-lfu 淘汰策略，LFU 策略通过统计访问频率，将访问频率最少的键值对淘汰。

### 八、持久化

Redis 是内存型数据库，为了保证数据在断电后不会丢失，需要将内存中的数据持久化到硬盘上。

#### RDB 持久化

将某个时间点的所有数据都存放到硬盘上。

可以将快照复制到其它服务器从而创建具有相同数据的服务器副本。

如果系统发生故障，将会丢失最后一次创建快照之后的数据。

如果数据量很大，保存快照的时间会很长。

#### AOF 持久化

将写命令添加到 AOF 文件（Append Only File）的末尾。

使用 AOF 持久化需要设置同步选项，从而确保写命令同步到磁盘文件上的时机。这是因为对文件进行写入并不会马上将内容同步到磁盘上，而是先存储到缓冲区，然后由操作系统决定什么时候同步到磁盘。有以下同步选项：

| 选项     | 同步频率                 |
| -------- | ------------------------ |
| always   | 每个写命令都同步         |
| everysec | 每秒同步一次             |
| no       | 让操作系统来决定何时同步 |

- always 选项会严重减低服务器的性能；
- everysec 选项比较合适，可以保证系统崩溃时只会丢失一秒左右的数据，并且 Redis 每秒执行一次同步对服务器性能几乎没有任何影响；
- no 选项并不能给服务器性能带来多大的提升，而且也会增加系统崩溃时数据丢失的数量。

随着服务器写请求的增多，AOF 文件会越来越大。Redis 提供了一种将 AOF 重写的特性，能够去除 AOF 文件中的冗余写命令。

### 九、事务

一个事务包含了多个命令，服务器在执行事务期间，不会改去执行其它客户端的命令请求。

事务中的多个命令被一次性发送给服务器，而不是一条一条发送，这种方式被称为流水线，它可以减少客户端与服务器之间的网络通信次数从而提升性能。

Redis 最简单的事务实现方式是使用 MULTI 和 EXEC 命令将事务操作包围起来。

### 十、事件

Redis 服务器是一个事件驱动程序。

#### 文件事件

服务器通过套接字与客户端或者其它服务器进行通信，文件事件就是对套接字操作的抽象。

Redis 基于 Reactor 模式开发了自己的网络事件处理器，使用 I/O 多路复用程序来同时监听多个套接字，并将到达的事件传送给文件事件分派器，分派器会根据套接字产生的事件类型调用相应的事件处理器。

[**TODO**]多路复用

#### 时间事件

服务器有一些操作需要在给定的时间点执行，时间事件是对这类定时操作的抽象。

时间事件又分为：

- 定时事件：是让一段程序在指定的时间之内执行一次；
- 周期性事件：是让一段程序每隔指定时间就执行一次。

Redis 将所有时间事件都放在一个无序链表中，通过遍历整个链表查找出已到达的时间事件，并调用相应的事件处理器。

#### 事件的调度与执行

服务器需要不断监听文件事件的套接字才能得到待处理的文件事件，但是不能一直监听，否则时间事件无法在规定的时间内执行，因此监听时间应该根据距离现在最近的时间事件来决定。

事件调度与执行由 aeProcessEvents 函数负责，伪代码如下：

```
def aeProcessEvents():
    # 获取到达时间离当前时间最接近的时间事件
    time_event = aeSearchNearestTimer()
    # 计算最接近的时间事件距离到达还有多少毫秒
    remaind_ms = time_event.when - unix_ts_now()
    # 如果事件已到达，那么 remaind_ms 的值可能为负数，将它设为 0
    if remaind_ms < 0:
        remaind_ms = 0
    # 根据 remaind_ms 的值，创建 timeval
    timeval = create_timeval_with_ms(remaind_ms)
    # 阻塞并等待文件事件产生，最大阻塞时间由传入的 timeval 决定
    aeApiPoll(timeval)
    # 处理所有已产生的文件事件
    procesFileEvents()
    # 处理所有已到达的时间事件
    processTimeEvents()
```

将 aeProcessEvents 函数置于一个循环里面，加上初始化和清理函数，就构成了 Redis 服务器的主函数，伪代码如下：

```
def main():
    # 初始化服务器
    init_server()
    # 一直处理事件，直到服务器关闭为止
    while server_is_not_shutdown():
        aeProcessEvents()
    # 服务器关闭，执行清理操作
    clean_server()
```

从事件处理的角度来看，服务器运行流程如下：

略

### 十一、复制

通过使用 slaveof host port 命令来让一个服务器成为另一个服务器的从服务器。

一个从服务器只能有一个主服务器，并且不支持主主复制。

#### 连接过程

1. 主服务器创建快照文件，发送给从服务器，并在发送期间使用缓冲区记录执行的写命令。快照文件发送完毕之后，开始向从服务器发送存储在缓冲区中的写命令；
2. 从服务器丢弃所有旧数据，载入主服务器发来的快照文件，之后从服务器开始接受主服务器发来的写命令；
3. 主服务器每执行一次写命令，就向从服务器发送相同的写命令。

#### 主从链

随着负载不断上升，主服务器可能无法很快地更新所有从服务器，或者重新连接和重新同步从服务器将导致系统超载。为了解决这个问题，可以创建一个中间层来分担主服务器的复制工作。中间层的服务器是最上层服务器的从服务器，又是最下层服务器的主服务器。

### 十二、Sentinel

Sentinel（哨兵）可以监听集群中的服务器，并在主服务器进入下线状态时，自动从从服务器中选举出新的主服务器。

### 十三、分片

分片是将数据划分为多个部分的方法，可以将数据存储到多台机器里面，这种方法在解决某些问题时可以获得线性级别的性能提升。

假设有 4 个 Redis 实例 R0，R1，R2，R3，还有很多表示用户的键 user:1，user:2，... ，有不同的方式来选择一个指定的键存储在哪个实例中。

- 最简单的方式是范围分片，例如用户 id 从 0~1000 的存储到实例 R0 中，用户 id 从 1001~2000 的存储到实例 R1 中，等等。但是这样需要维护一张映射范围表，维护操作代价很高。
- 还有一种方式是哈希分片，使用 CRC32 哈希函数将键转换为一个数字，再对实例数量求模就能知道应该存储的实例。

根据执行分片的位置，可以分为三种分片方式：

- 客户端分片：客户端使用一致性哈希等算法决定键应当分布到哪个节点。
- 代理分片：将客户端请求发送到代理上，由代理转发请求到正确的节点上。
- 服务器分片：Redis Cluster。

### 十四、一个简单的论坛系统分析

该论坛系统功能如下：

- 可以发布文章；
- 可以对文章进行点赞；
- 在首页可以按文章的发布时间或者文章的点赞数进行排序显示。

#### 文章信息

文章包括标题、作者、赞数等信息，在关系型数据库中很容易构建一张表来存储这些信息，在 Redis 中可以使用 HASH 来存储每种信息以及其对应的值的映射。

Redis 没有关系型数据库中的表这一概念来将同种类型的数据存放在一起，而是使用命名空间的方式来实现这一功能。键名的前面部分存储命名空间，后面部分的内容存储 ID，通常使用 : 来进行分隔。例如下面的 HASH 的键名为 article:92617，其中 article 为命名空间，ID 为 92617。

#### 点赞功能

当有用户为一篇文章点赞时，除了要对该文章的 votes 字段进行加 1 操作，还必须记录该用户已经对该文章进行了点赞，防止用户点赞次数超过 1。可以建立文章的已投票用户集合来进行记录。

为了节约内存，规定一篇文章发布满一周之后，就不能再对它进行投票，而文章的已投票集合也会被删除，可以为文章的已投票集合设置一个一周的过期时间就能实现这个规定。

#### 对文章进行排序

为了按发布时间和点赞数进行排序，可以建立一个文章发布时间的有序集合和一个文章点赞数的有序集合。（下图中的 score 就是这里所说的点赞数；下面所示的有序集合分值并不直接是时间和点赞数，而是根据时间和点赞数间接计算出来的）

### 参考资料

- Carlson J L. Redis in Action[J]. Media.johnwiley.com.au, 2013.
- [黄健宏. Redis 设计与实现 [M\]. 机械工业出版社, 2014.](http://redisbook.com/index.html)
- [REDIS IN ACTION](https://redislabs.com/ebook/foreword/)
- [Skip Lists: Done Right](http://ticki.github.io/blog/skip-lists-done-right/)
- [论述 Redis 和 Memcached 的差异](http://www.cnblogs.com/loveincode/p/7411911.html)
- [Redis 3.0 中文版- 分片](http://wiki.jikexueyuan.com/project/redis-guide)
- [Redis 应用场景](http://www.scienjus.com/redis-use-case/)
- [Using Redis as an LRU cache](https://redis.io/topics/lru-cache)