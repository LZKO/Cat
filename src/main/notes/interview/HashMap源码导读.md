# HashMap源码导读

[TOC]







红黑树这段看不很懂，做点笔记

## 一、TreeNode类的treeify()方法

### 1、概述

treeify方法是TreeNode类的一个实例方法，通过TreeNode对象调用，实现该对象打头的链表转换为树结构。

### 2、方法解析

```java
/**
 * 参数为HashMap的元素数组
 */
final void treeify(Node<K,V>[] tab) {
    TreeNode<K,V> root = null; // 定义树的根节点
    for (TreeNode<K,V> x = this, next; x != null; x = next) { // 遍历链表，x指向当前节点、next指向下一个节点
        next = (TreeNode<K,V>)x.next; // 下一个节点
        x.left = x.right = null; // 设置当前节点的左右节点为空
        if (root == null) { // 如果还没有根节点
            x.parent = null; // 当前节点的父节点设为空
            x.red = false; // 当前节点的红色属性设为false（把当前节点设为黑色）
            root = x; // 根节点指向到当前节点
        }
        else { // 如果已经存在根节点了
            K k = x.key; // 取得当前链表节点的key
            int h = x.hash; // 取得当前链表节点的hash值
            Class<?> kc = null; // 定义key所属的Class
            for (TreeNode<K,V> p = root;;) { // 从根节点开始遍历，此遍历没有设置边界，只能从内部跳出
                // GOTO1
                int dir, ph; // dir 标识方向（左右）、ph标识当前树节点的hash值
                K pk = p.key; // 当前树节点的key
                if ((ph = p.hash) > h) // 如果当前树节点hash值 大于 当前链表节点的hash值
                    dir = -1; // 标识当前链表节点会放到当前树节点的左侧
                else if (ph < h)
                    dir = 1; // 右侧
 
                /*
                 * 如果两个节点的key的hash值相等，那么还要通过其他方式再进行比较
                 * 如果当前链表节点的key实现了comparable接口，并且当前树节点和链表节点是相同Class的实例，那么通过comparable的方式再比较两者。
                 * 如果还是相等，最后再通过tieBreakOrder比较一次
                 */
                else if ((kc == null &&
                            (kc = comparableClassFor(k)) == null) ||
                            (dir = compareComparables(kc, k, pk)) == 0)
                    dir = tieBreakOrder(k, pk);
 
                TreeNode<K,V> xp = p; // 保存当前树节点
 
                /*
                 * 如果dir 小于等于0 ： 当前链表节点一定放置在当前树节点的左侧，但不一定是该树节点的左孩子，也可能是左孩子的右孩子 或者 更深层次的节点。
                 * 如果dir 大于0 ： 当前链表节点一定放置在当前树节点的右侧，但不一定是该树节点的右孩子，也可能是右孩子的左孩子 或者 更深层次的节点。
                 * 如果当前树节点不是叶子节点，那么最终会以当前树节点的左孩子或者右孩子 为 起始节点  再从GOTO1 处开始 重新寻找自己（当前链表节点）的位置
                 * 如果当前树节点就是叶子节点，那么根据dir的值，就可以把当前链表节点挂载到当前树节点的左或者右侧了。
                 * 挂载之后，还需要重新把树进行平衡。平衡之后，就可以针对下一个链表节点进行处理了。
                 */
                if ((p = (dir <= 0) ? p.left : p.right) == null) {
                    x.parent = xp; // 当前链表节点 作为 当前树节点的子节点
                    if (dir <= 0)
                        xp.left = x; // 作为左孩子
                    else
                        xp.right = x; // 作为右孩子
                    root = balanceInsertion(root, x); // 重新平衡
                    break;
                }
            }
        }
    }
 
    // 把所有的链表节点都遍历完之后，最终构造出来的树可能经历多个平衡操作，根节点目前到底是链表的哪一个节点是不确定的
    // 因为我们要基于树来做查找，所以就应该把 tab[N] 得到的对象一定根节点对象，而目前只是链表的第一个节点对象，所以要做相应的处理。
    moveRootToFront(tab, root); // 单独解析
```

3、参考

[JDK8：HashMap源码解析：TreeNode类的treeify方法](https://blog.csdn.net/weixin_42340670/article/details/80531795)



## 二、TreeNode类的balanceInsertion方法

### 1、概述

balanceInsertion指的是红黑树的插入平衡算法，当树结构中新插入了一个节点后，要对树进行重新的结构化，以保证该树始终维持红黑树的特性。

关于红黑树的特性：

性质1. 节点是红色或黑色。

性质2. 根节点是黑色。

性质3 每个叶节点（NIL节点，空节点）是黑色的。

性质4 每个红色节点的两个子节点都是黑色。(从每个叶子到根的所有路径上不能有两个连续的红色节点)

性质5. 从任一节点到其每个叶子的路径上包含的黑色节点数量都相同。

### 2、方法解析

```java

/**
 * 红黑树插入节点后，需要重新平衡
 * root 当前根节点
 * x 新插入的节点
 * 返回重新平衡后的根节点
 */
static <K,V> TreeNode<K,V> balanceInsertion(TreeNode<K,V> root,
                                                    TreeNode<K,V> x) {
    x.red = true; // 新插入的节点标为红色
 
    /*
     * 这一步即定义了变量，又开起了循环，循环没有控制条件，只能从内部跳出
     * xp：当前节点的父节点、xpp：爷爷节点、xppl：左叔叔节点、xppr：右叔叔节点
     */
    for (TreeNode<K,V> xp, xpp, xppl, xppr;;) { 
 
        // 如果父节点为空、说明当前节点就是根节点，那么把当前节点标为黑色，返回当前节点
        if ((xp = x.parent) == null) { // L1
            x.red = false;
            return x;
        }
 
        // 父节点不为空
        // 如果父节点为黑色 或者 【（父节点为红色 但是 爷爷节点为空） -> 这种情况何时出现？】
        else if (!xp.red || (xpp = xp.parent) == null) // L2
            return root;
        if (xp == (xppl = xpp.left)) { // 如果父节点是爷爷节点的左孩子  // L3
            if ((xppr = xpp.right) != null && xppr.red) { // 如果右叔叔不为空 并且 为红色  // L3_1
                xppr.red = false; // 右叔叔置为黑色
                xp.red = false; // 父节点置为黑色
                xpp.red = true; // 爷爷节点置为红色
                x = xpp; // 运行到这里之后，就又会进行下一轮的循环了，将爷爷节点当做处理的起始节点 
            }
            else { // 如果右叔叔为空 或者 为黑色 // L3_2
                if (x == xp.right) { // 如果当前节点是父节点的右孩子 // L3_2_1
                    root = rotateLeft(root, x = xp); // 父节点左旋，见下文左旋方法解析
                    xpp = (xp = x.parent) == null ? null : xp.parent; // 获取爷爷节点
                }
                if (xp != null) { // 如果父节点不为空 // L3_2_2
                    xp.red = false; // 父节点 置为黑色
                    if (xpp != null) { // 爷爷节点不为空
                        xpp.red = true; // 爷爷节点置为 红色
                        root = rotateRight(root, xpp);  //爷爷节点右旋，见下文右旋方法解析
                    }
                }
            }
        }
        else { // 如果父节点是爷爷节点的右孩子 // L4
            if (xppl != null && xppl.red) { // 如果左叔叔是红色 // L4_1
                xppl.red = false; // 左叔叔置为 黑色
                xp.red = false; // 父节点置为黑色
                xpp.red = true; // 爷爷置为红色
                x = xpp; // 运行到这里之后，就又会进行下一轮的循环了，将爷爷节点当做处理的起始节点 
            }
            else { // 如果左叔叔为空或者是黑色 // L4_2
                if (x == xp.left) { // 如果当前节点是个左孩子 // L4_2_1
                    root = rotateRight(root, x = xp); // 针对父节点做右旋，见下文右旋方法解析
                    xpp = (xp = x.parent) == null ? null : xp.parent; // 获取爷爷节点
                }
                if (xp != null) { // 如果父节点不为空 // L4_2_4
                    xp.red = false; // 父节点置为黑色
                    if (xpp != null) { //如果爷爷节点不为空
                        xpp.red = true; // 爷爷节点置为红色
                        root = rotateLeft(root, xpp); // 针对爷爷节点做左旋
                    }
                }
            }
        }
    }
}
 
 
/**
 * 节点左旋
 * root 根节点
 * p 要左旋的节点
 */
static <K,V> TreeNode<K,V> rotateLeft(TreeNode<K,V> root,
                                              TreeNode<K,V> p) {
    TreeNode<K,V> r, pp, rl;
    if (p != null && (r = p.right) != null) { // 要左旋的节点以及要左旋的节点的右孩子不为空
        if ((rl = p.right = r.left) != null) // 要左旋的节点的右孩子的左节点 赋给 要左旋的节点的右孩子 节点为：rl
            rl.parent = p; // 设置rl和要左旋的节点的父子关系【之前只是爹认了孩子，孩子还没有答应，这一步孩子也认了爹】
 
        // 将要左旋的节点的右孩子的父节点  指向 要左旋的节点的父节点，相当于右孩子提升了一层，
        // 此时如果父节点为空， 说明r 已经是顶层节点了，应该作为root 并且标为黑色
        if ((pp = r.parent = p.parent) == null) 
            (root = r).red = false;
        else if (pp.left == p) // 如果父节点不为空 并且 要左旋的节点是个左孩子
            pp.left = r; // 设置r和父节点的父子关系【之前只是孩子认了爹，爹还没有答应，这一步爹也认了孩子】
        else // 要左旋的节点是个右孩子
            pp.right = r; 
        r.left = p; // 要左旋的节点  作为 他的右孩子的左节点
        p.parent = r; // 要左旋的节点的右孩子  作为  他的父节点
    }
    return root; // 返回根节点
}
 
/**
 * 节点右旋
 * root 根节点
 * p 要右旋的节点
 */
static <K,V> TreeNode<K,V> rotateRight(TreeNode<K,V> root,
                                               TreeNode<K,V> p) {
    TreeNode<K,V> l, pp, lr;
    if (p != null && (l = p.left) != null) { // 要右旋的节点不为空以及要右旋的节点的左孩子不为空
        if ((lr = p.left = l.right) != null) // 要右旋的节点的左孩子的右节点 赋给 要右旋节点的左孩子 节点为：lr
            lr.parent = p; // 设置lr和要右旋的节点的父子关系【之前只是爹认了孩子，孩子还没有答应，这一步孩子也认了爹】
 
        // 将要右旋的节点的左孩子的父节点  指向 要右旋的节点的父节点，相当于左孩子提升了一层，
        // 此时如果父节点为空， 说明l 已经是顶层节点了，应该作为root 并且标为黑色
        if ((pp = l.parent = p.parent) == null) 
            (root = l).red = false;
        else if (pp.right == p) // 如果父节点不为空 并且 要右旋的节点是个右孩子
            pp.right = l; // 设置l和父节点的父子关系【之前只是孩子认了爹，爹还没有答应，这一步爹也认了孩子】
        else // 要右旋的节点是个左孩子
            pp.left = l; // 同上
        l.right = p; // 要右旋的节点 作为 他左孩子的右节点
        p.parent = l; // 要右旋的节点的父节点 指向 他的左孩子
    }
    return root;
}
```



### 3、图例

#### 3.1、无旋转

![](C:\lyy\project_workspace\Cat\src\main\resources\pic\java\hashmap\hashmap1.jpg)

测试图片

![](https://github.com/LZKO/Cat/blob/master/src/main/resources/pic/java/hashmap/hashmap1.jpg)







#### 3.2、有旋转

![](C:\lyy\project_workspace\Cat\src\main\resources\pic\java\hashmap\hashmap2.jpg)

![](C:\lyy\project_workspace\Cat\src\main\resources\pic\java\hashmap\hashmap3.jpg)

4、参考

[JDK8：HashMap源码解析：TreeNode类的balanceInsertion方法](https://blog.csdn.net/weixin_42340670/article/details/80550932)



## 三、TreeNode类的moveRootToFront方法

### 1、概述

TreeNode在增加或删除节点后，都需要对整个树重新进行平衡，平衡之后的根节点也许就会发生变化，此时为了保证：如果HashMap元素数组根据下标取得的元素是一个TreeNode类型，那么这个TreeNode节点一定要是这颗树的根节点，同时也要是整个链表的首节点。

### 2、方法解析

```java

/**
 * 把红黑树的根节点设为  其所在的数组槽 的第一个元素
 * 首先明确：TreeNode既是一个红黑树结构，也是一个双链表结构
 * 这个方法里做的事情，就是保证树的根节点一定也要成为链表的首节点
 */
static <K,V> void moveRootToFront(Node<K,V>[] tab, TreeNode<K,V> root) {
    int n;
    if (root != null && tab != null && (n = tab.length) > 0) { // 根节点不为空 并且 HashMap的元素数组不为空
        int index = (n - 1) & root.hash; // 根据根节点的Hash值 和 HashMap的元素数组长度  取得根节点在数组中的位置
        TreeNode<K,V> first = (TreeNode<K,V>)tab[index]; // 首先取得该位置上的第一个节点对象
        if (root != first) { // 如果该节点对象 与 根节点对象 不同
            Node<K,V> rn; // 定义根节点的后一个节点
            tab[index] = root; // 把元素数组index位置的元素替换为根节点对象
            TreeNode<K,V> rp = root.prev; // 获取根节点对象的前一个节点
            if ((rn = root.next) != null) // 如果后节点不为空 
                ((TreeNode<K,V>)rn).prev = rp; // root后节点的前节点  指向到 root的前节点，相当于把root从链表中摘除
            if (rp != null) // 如果root的前节点不为空
                rp.next = rn; // root前节点的后节点 指向到 root的后节点
            if (first != null) // 如果数组该位置上原来的元素不为空
                first.prev = root; // 这个原有的元素的 前节点 指向到 root，相当于root目前位于链表的首位
            root.next = first; // 原来的第一个节点现在作为root的下一个节点，变成了第二个节点
            root.prev = null; // 首节点没有前节点
        }
 
        /*
         * 这一步是防御性的编程
         * 校验TreeNode对象是否满足红黑树和双链表的特性
         * 如果这个方法校验不通过：可能是因为用户编程失误，破坏了结构（例如：并发场景下）；也可能是TreeNode的实现有问题（这个是理论上的以防万一）；
         */ 
        assert checkInvariants(root); 
    }
}	
```

### 3、参考

[JDK8：HashMap源码解析：TreeNode类的moveRootToFront方法](https://blog.csdn.net/weixin_42340670/article/details/80555860)



## 四、TreeNode的untreeify

### 1、概述

反树化，即将红黑树转换成链表

### 2、方法解析

```java
final Node<K, V> untreeify(HashMap<K, V> map) {
            //hd 链表头节点
            //tl 链表尾节点
            Node<K, V> hd = null, tl = null;
            //q为当前使用方法的TreeNode，在q节点从Node转为TreeNode之前的链表关系还在，遍历链表
            for (Node<K, V> q = this; q != null; q = q.next) {
                //将q节点从TreeNode转为Node p
                Node<K, V> p = map.replacementNode(q, null);
                //如果tl节点为空，将hd节点指向p节点
                if (tl == null) {
                    hd = p;
                } else {
                    //如果tl节点不为空，则将tl的后节点指向p
                    tl.next = p;
                }
                //将tl节点指向p
                tl = p;
            }
            //返回hd节点
            return hd;
        }	
```



3、参考

[Java 8 HashMap（终）——TreeNode的untreeify和split方法](https://blog.csdn.net/weixin_48872249/article/details/115422757)



## 五、TreeNode类的putTreeVal方法

### 1、概述

我们都知道，目前HashMap是采用数组+链表+红黑树的方式来存储和组织数据的。

在put数据的时候，根据键的hash值寻址到具体数组位置，如果不存在hash碰撞，那么这个位置就只存储这么一个键值对。参见：put方法分析

如果两个key的hash值相同，那么对应数组位置上就需要用链表的方式将这两个数据组织起来，当同一个位置上链表中的元素达到8个的时候，就会再将这些元素构建成一个红黑树（参见：treeifyBin方法分析），同时把原来的单链表结构变成了双链表结构，也就是这些元素即维持着红黑树的结构又维持着双链表的结构。当第9个相同hash值的键值对put过来时，发现该位置已经是一个树节点了，那么就会调用putTreeVal方法，将这个新的值设置到指定的key上。

### 2、方法解析

```java
/**
 * 当存在hash碰撞的时候，且元素数量大于8个时候，就会以红黑树的方式将这些元素组织起来
 * map 当前节点所在的HashMap对象
 * tab 当前HashMap对象的元素数组
 * h   指定key的hash值
 * k   指定key
 * v   指定key上要写入的值
 * 返回：指定key所匹配到的节点对象，针对这个对象去修改V（返回空说明创建了一个新节点）
 */
final TreeNode<K,V> putTreeVal(HashMap<K,V> map, Node<K,V>[] tab,
                                int h, K k, V v) {
    Class<?> kc = null; // 定义k的Class对象
    boolean searched = false; // 标识是否已经遍历过一次树，未必是从根节点遍历的，但是遍历路径上一定已经包含了后续需要比对的所有节点。
    TreeNode<K,V> root = (parent != null) ? root() : this; // 父节点不为空那么查找根节点，为空那么自身就是根节点
    for (TreeNode<K,V> p = root;;) { // 从根节点开始遍历，没有终止条件，只能从内部退出
        int dir, ph; K pk; // 声明方向、当前节点hash值、当前节点的键对象
        if ((ph = p.hash) > h) // 如果当前节点hash 大于 指定key的hash值
            dir = -1; // 要添加的元素应该放置在当前节点的左侧
        else if (ph < h) // 如果当前节点hash 小于 指定key的hash值
            dir = 1; // 要添加的元素应该放置在当前节点的右侧
        else if ((pk = p.key) == k || (k != null && k.equals(pk))) // 如果当前节点的键对象 和 指定key对象相同
            return p; // 那么就返回当前节点对象，在外层方法会对v进行写入
 
        // 走到这一步说明 当前节点的hash值  和 指定key的hash值  是相等的，但是equals不等
        else if ((kc == null &&
                    (kc = comparableClassFor(k)) == null) ||
                    (dir = compareComparables(kc, k, pk)) == 0) {
 
            // 走到这里说明：指定key没有实现comparable接口   或者   实现了comparable接口并且和当前节点的键对象比较之后相等（仅限第一次循环）
        
 
            /*
             * searched 标识是否已经对比过当前节点的左右子节点了
             * 如果还没有遍历过，那么就递归遍历对比，看是否能够得到那个键对象equals相等的的节点
             * 如果得到了键的equals相等的的节点就返回
             * 如果还是没有键的equals相等的节点，那说明应该创建一个新节点了
             */
            if (!searched) { // 如果还没有比对过当前节点的所有子节点
                TreeNode<K,V> q, ch; // 定义要返回的节点、和子节点
                searched = true; // 标识已经遍历过一次了
                /*
                 * 红黑树也是二叉树，所以只要沿着左右两侧遍历寻找就可以了
                 * 这是个短路运算，如果先从左侧就已经找到了，右侧就不需要遍历了
                 * find 方法内部还会有递归调用。参见：find方法解析
                 */
                if (((ch = p.left) != null &&
                        (q = ch.find(h, k, kc)) != null) ||
                    ((ch = p.right) != null &&
                        (q = ch.find(h, k, kc)) != null))
                    return q; // 找到了指定key键对应的
            }
 
            // 走到这里就说明，遍历了所有子节点也没有找到和当前键equals相等的节点
            dir = tieBreakOrder(k, pk); // 再比较一下当前节点键和指定key键的大小
        }
 
        TreeNode<K,V> xp = p; // 定义xp指向当前节点
        /*
        * 如果dir小于等于0，那么看当前节点的左节点是否为空，如果为空，就可以把要添加的元素作为当前节点的左节点，如果不为空，还需要下一轮继续比较
        * 如果dir大于等于0，那么看当前节点的右节点是否为空，如果为空，就可以把要添加的元素作为当前节点的右节点，如果不为空，还需要下一轮继续比较
        * 如果以上两条当中有一个子节点不为空，这个if中还做了一件事，那就是把p已经指向了对应的不为空的子节点，开始下一轮的比较
        */
        if ((p = (dir <= 0) ? p.left : p.right) == null) {  
            // 如果恰好要添加的方向上的子节点为空，此时节点p已经指向了这个空的子节点
            Node<K,V> xpn = xp.next; // 获取当前节点的next节点
            TreeNode<K,V> x = map.newTreeNode(h, k, v, xpn); // 创建一个新的树节点
            if (dir <= 0)
                xp.left = x;  // 左孩子指向到这个新的树节点
            else
                xp.right = x; // 右孩子指向到这个新的树节点
            xp.next = x; // 链表中的next节点指向到这个新的树节点
            x.parent = x.prev = xp; // 这个新的树节点的父节点、前节点均设置为 当前的树节点
            if (xpn != null) // 如果原来的next节点不为空
                ((TreeNode<K,V>)xpn).prev = x; // 那么原来的next节点的前节点指向到新的树节点
            moveRootToFront(tab, balanceInsertion(root, x));// 重新平衡，以及新的根节点置顶
            return null; // 返回空，意味着产生了一个新节点
        }
    }
}
```



### 3、参考

[JDK8：HashMap源码解析：TreeNode类的putTreeVal方法](https://blog.csdn.net/weixin_42340670/article/details/80635008)

## 六、remove方法、removeNode方法

### 1、概述

在HashMap中如果要根据key删除这个key对应的键值对，需要调用remove(key)方法，该方法将会根据查找到匹配的键值对，将其从HashMap中删除，并且返回键值对的值。

### 2、方法解析

我们先来看remove方法

```java

/**
* 从HashMap中删除掉指定key对应的键值对，并返回被删除的键值对的值
* 如果返回空，说明key可能不存在，也可能key对应的值就是null
* 如果想确定到底key是否存在可以使用containsKey方法
*/
public V remove(Object key) {
    Node<K,V> e; // 定义一个节点变量，用来存储要被删除的节点（键值对）
    return (e = removeNode(hash(key), key, null, false, true)) == null ?
        null : e.value; // 调用removeNode方法
}
```

可以发现remove方法底层实际上是调用了removeNode方法来删除键值对节点，并且根据返回的节点对象取得key对应的值，那么我们再来详细分析下removeNode方法的代码

```java
/**
* 方法为final，不可被覆写，子类可以通过实现afterNodeRemoval方法来增加自己的处理逻辑（解析中有描述）
*
* @param hash key的hash值，该值是通过hash(key)获取到的
* @param key 要删除的键值对的key
* @param value 要删除的键值对的value，该值是否作为删除的条件取决于matchValue是否为true
* @param matchValue 如果为true，则当key对应的键值对的值equals(value)为true时才删除；否则不关心value的值
* @param movable 删除后是否移动节点，如果为false，则不移动
* @return 返回被删除的节点对象，如果没有删除任何节点则返回null
*/
final Node<K,V> removeNode(int hash, Object key, Object value,
                            boolean matchValue, boolean movable) {
    Node<K,V>[] tab; Node<K,V> p; int n, index; // 声明节点数组、当前节点、数组长度、索引值
    /*
     * 如果 节点数组tab不为空、数组长度n大于0、根据hash定位到的节点对象p（该节点为 树的根节点 或 链表的首节点）不为空
     * 需要从该节点p向下遍历，找到那个和key匹配的节点对象
     */
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (p = tab[index = (n - 1) & hash]) != null) {
        Node<K,V> node = null, e; K k; V v; // 定义要返回的节点对象，声明一个临时节点变量、键变量、值变量
 
        // 如果当前节点的键和key相等，那么当前节点就是要删除的节点，赋值给node
        if (p.hash == hash &&
            ((k = p.key) == key || (key != null && key.equals(k))))
            node = p;
 
        /*
         * 到这一步说明首节点没有匹配上，那么检查下是否有next节点
         * 如果没有next节点，就说明该节点所在位置上没有发生hash碰撞, 就一个节点并且还没匹配上，也就没得删了，最终也就返回null了
         * 如果存在next节点，就说明该数组位置上发生了hash碰撞，此时可能存在一个链表，也可能是一颗红黑树
         */
        else if ((e = p.next) != null) {
            // 如果当前节点是TreeNode类型，说明已经是一个红黑树，那么调用getTreeNode方法从树结构中查找满足条件的节点
            if (p instanceof TreeNode)
                node = ((TreeNode<K,V>)p).getTreeNode(hash, key);
            // 如果不是树节点，那么就是一个链表，只需要从头到尾逐个节点比对即可    
            else {
                do {
                    // 如果e节点的键是否和key相等，e节点就是要删除的节点，赋值给node变量，调出循环
                    if (e.hash == hash &&
                        ((k = e.key) == key ||
                            (key != null && key.equals(k)))) {
                        node = e;
                        break;
                    }
 
                    // 走到这里，说明e也没有匹配上
                    p = e; // 把当前节点p指向e，这一步是让p存储的永远下一次循环里e的父节点，如果下一次e匹配上了，那么p就是node的父节点
                } while ((e = e.next) != null); // 如果e存在下一个节点，那么继续去匹配下一个节点。直到匹配到某个节点跳出 或者 遍历完链表所有节点
            }
        }
 
        /*
         * 如果node不为空，说明根据key匹配到了要删除的节点
         * 如果不需要对比value值  或者  需要对比value值但是value值也相等
         * 那么就可以删除该node节点了
         */
        if (node != null && (!matchValue || (v = node.value) == value ||
                                (value != null && value.equals(v)))) {
            if (node instanceof TreeNode) // 如果该节点是个TreeNode对象，说明此节点存在于红黑树结构中，调用removeTreeNode方法（该方法单独解析）移除该节点
                ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
            else if (node == p) // 如果该节点不是TreeNode对象，node == p 的意思是该node节点就是首节点
                tab[index] = node.next; // 由于删除的是首节点，那么直接将节点数组对应位置指向到第二个节点即可
            else // 如果node节点不是首节点，此时p是node的父节点，由于要删除node，所有只需要把p的下一个节点指向到node的下一个节点即可把node从链表中删除了
                p.next = node.next;
            ++modCount; // HashMap的修改次数递增
            --size; // HashMap的元素个数递减
            afterNodeRemoval(node); // 调用afterNodeRemoval方法，该方法HashMap没有任何实现逻辑，目的是为了让子类根据需要自行覆写
            return node;
        }
    }
    return null;
}
```

### 3、参考

[JDK8：HashMap源码解析：remove方法、removeNode方法](https://blog.csdn.net/weixin_42340670/article/details/81139900)



## 七、TreeNode类的removeTreeNode方法

### 1、概述

略

### 2、方法解析

这两个方法没看懂，先记录

#### 2.1、removeTreeNode

```java
  /**
         * @param movable 处理时，是否移动其他节点
         */
        final void removeTreeNode(HashMap<K, V> map, Node<K, V>[] tab, boolean movable) {
            int n;
            //数组为空直接返回
            if (tab == null || (n = tab.length) == 0) {
                return;
            }
            //计算当前节点的的位置
            int index = (n - 1) & hash;
            //找到这个位置上的第一个树节点first，并标记为root节点
            TreeNode<K, V> first = (TreeNode<K, V>) tab[index], root = first, rl;
            //succ 表示当前要删除的节点的后节点， pred 表示其前节点
            TreeNode<K, V> succ = (TreeNode<K, V>) this.next, pred = this.prev;
            if (pred == null) {
                //如果pred节点不存在，则表示当前节点为根节点
                //删除后succ节点成为根节点，用fist节点标记，并放在数组上
                tab[index] = first = succ;
            } else {
                //如果pred节点存在
                //则将pred节点的后节点指向succ节点
                pred.next = succ;
            }
            if (succ != null) {
                //如果succ节点存在，则将succ节点的前节点指向pred节点
                succ.prev = pred;
            }
            if (first == null) {
                //如果当前节点不存在，直接返回
                return;
            }
            //如果root节点的父节点存在，说明当前root节点不是根节点
            if (root.parent != null) {
                //获取真实根节点
                root = root.root();
            }
            //以上做法是先整理当前节点上的链表关系，接下来整理红黑树关系
            //根据当root节点和它的左右节点的一些情况，判断红黑树节点的数量
            if (root == null || root.right == null || (rl = root.left) == null || rl.left == null) {
                //太小了，转换为链表
                tab[index] = first.untreeify(map);
                return;
            }
            //p 当前节点，pl p节点的左节点，pr p节点的右节点
            //replacement p节点删除后代替他的节点
            TreeNode<K, V> p = this, pl = p.left, pr = p.right, replacement;
            if (pl != null && pr != null) {
                //当删除p节点，但是他的左右节点不为空时，遍历他的右节点上的左子树(以下操作先让p节点和s节点交换位置，然后找到replacement节点替换他)
                TreeNode<K, V> s = pr, sl;
                while ((sl = s.left) != null) {
                    s = sl;
                }
                //通过上述操作，s节点是大于p节点的最小节点（替换他的节点）
                //将s节点和p节点的颜色交换
                boolean c = s.red;
                s.red = p.red;
                p.red = c;
                //sr s节点的右节点
                TreeNode<K, V> sr = s.right;
                //pp p节点的父节点
                TreeNode<K, V> pp = p.parent;
                //如果pr节点就是s节点
                if (s == pr) {
                    //交换他们的关系
                    p.parent = s;
                    s.right = p;
                } else {
                    //获得s节点的父节点sp
                    TreeNode<K, V> sp = s.parent;
                    //将p节点的父节点指向sp，且sp节点存在
                    if ((p.parent = sp) != null) {
                        //判断s节点在sp节点的哪一侧，将p节点放在s节点的那一侧
                        if (s == sp.left) {
                            sp.left = p;
                        } else {
                            sp.right = p;
                        }
                    }
                    //将pr节点变成s节点的右节点，且pr节点存在
                    if ((s.right = pr) != null) {
                        //将s节点变成pr节点的父节点
                        pr.parent = s;
                    }
                }
                //因为s节点的性质，s节点没有左节点
                //当下p节点和s节点交换了位置，所以将p节点的左节点指空
                p.left = null;
                //将sr节点的变成p节点的右节点，且sr节点存在
                if ((p.right = sr) != null) {
                    //将p节点变成sr节点的父节点
                    sr.parent = p;
                }
                //将pl节点变成s节点的左节点，且pl节点存在
                if ((s.left = pl) != null) {
                    //将s节点变成pl节点的父节点
                    pl.parent = s;
                }
                //如果pp节点不存在，那当前s节点就是根节点
                if ((s.parent = pp) == null) {
                    //将root节点指向s节点
                    root = s;
                } else if (p == pp.left) {
                    //如果pp节点存在，且p节点是pp节点的左节点
                    //将s节点变成pp节点的左节点
                    pp.left = s;
                } else {
                    //将s节点变成pp节点的右节点
                    pp.right = s;
                }
                //如果sr节点存在
                if (sr != null) {
                    //将replacement节点变成sr节点
                    replacement = sr;
                } else {
                    //sr节点不存在，将replacement变成p节点
                    replacement = p;
                }
            } else if (pl != null) {
                //如果pl节点存在，pr节点不存在，不用交换位置，pl节点成为replacement节点
                replacement = pl;
            } else if (pr != null) {
                //如果pr节点存在，pl节点不存在，不用交换位置，pr节点成为replacement节点
                replacement = pr;
            } else {
                //如果都不存在，p节点成为replacement节点
                replacement = p;
            }
            //将以下判断跟上述判断分开看，仅以p节点的当前位置性质，对replacement节点进行操作
            if (replacement != p) {
                //如果replacement不是p节点
                //将p节点的父节点pp变成replacement节点的父节点
                TreeNode<K, V> pp = replacement.parent = p.parent;
                //如果pp节点不存在
                if (pp == null) {
                    //replacement节点成为根节点
                    root = replacement;
                } else if (p == pp.left) {
                    //如果pp节点存在，根据p节点在pp节点的位置，设置replacement节点的位置
                    pp.left = replacement;
                } else {
                    pp.right = replacement;
                }
                //将p节点的所有树关系关系指空
                p.left = p.right = p.parent = null;
            }
            //如果p节点是红色，删除后不影响root节点，如果是黑色，找到平衡后的根节点，并用节点r表示
            TreeNode<K, V> r = p.red ? root : balanceDeletion(root, replacement);
            //如果p节点是replacement节点
            if (replacement == p) {
                //得到p节点父节点pp
                TreeNode<K, V> pp = p.parent;
                //将p节点的父节点指空
                p.parent = null;
                if (pp != null) {
                    //如果pp节点存在
                    //根据p节点的位置，将pp节点的对应位置指空
                    if (p == pp.left) {
                        pp.left = null;
                    } else if (p == pp.right) {
                        pp.right = null;
                    }
                }
            }
            //移动新的跟节点到数组上
            if (movable) {
                moveRootToFront(tab, r);
            }
        }
```

#### 2.2、balanceDeletion

```java
 static <K, V> TreeNode<K, V> balanceDeletion(TreeNode<K, V> root, TreeNode<K, V> x) {
            //x 当前要删除的节点
            //xp x节点的父节点
            //xpl xp节点的左节点
            //xpr xp节点的右节点
            TreeNode<K, V> xp, xpl, xpr;
            for (; ; ) {
                if (x == null || x == root) {
                    //如果x节点为空，或x节点是根节点
                    return root;
                } else if ((xp = x.parent) == null) {
                    //当xp节点为空时，说明x为根节点，将x节点设置为黑色，并返回x节点
                    x.red = false;
                    return x;
                } else if (x.red) {
                    //x节点是红色，无需调整
                    x.red = false;
                    return root;
                } else if ((xpl = xp.left) == x) {
                    //如果x节点为xpl节点
                    if ((xpr = xp.right) != null && xpr.red) {
                        //如果xpr节点不为空，且xpr节点是红色的
                        //将xpr设置为黑色，xp设置为红色
                        xpr.red = false;
                        xp.red = true;
                        //左旋
                        root = rotateLeft(root, xp);
                        //重新将xp节点指向x节点的父节点，并将xpr节点指向xp的右节点
                        xpr = (xp = x.parent) == null ? null : xp.right;
                    }
                    if (xpr == null) {
                        //若xpr节点不存在
                        //则将x节点指向xp节点向上调整
                        x = xp;
                    } else {
                        //sl xpr节点的左节点
                        //sr xpr节点的右节点
                        TreeNode<K, V> sl = xpr.left, sr = xpr.right;
                        if ((sr == null || !sr.red) &&
                                (sl == null || !sl.red)) {
                            //若sr节点为空或者sr节点是黑色的，且sl节点为空或者sl节点是黑色的
                            //将xpr节点变成红色
                            xpr.red = true;
                            //则将x节点指向xp节点向上调整
                            x = xp;
                        } else {
                            //sr和sl中存在一个红节点
                            if (sr == null || !sr.red) {
                                //此处说明sl是红节点,将sl节点设置为黑色
                                sl.red = false;
                                //将xpr节点设置为红色
                                xpr.red = true;
                                //右旋
                                root = rotateRight(root, xpr);
                                //将xpr节点重新指向xp节点的右节点
                                xpr = (xp = x.parent) == null ? null : xp.right;
                            }
                            if (xpr != null) {
                                //如果xpr节点不为空,让xpr节点与xp节点同色
                                xpr.red = (xp == null) ? false : xp.red;
                                //当sr节点不为空，变成黑色
                                if ((sr = xpr.right) != null) {
                                    sr.red = false;
                                }
                            }
                            //存在xp节点
                            if (xp != null) {
                                //将xp节点设置为黑色
                                xp.red = false;
                                //进行左旋
                                root = rotateLeft(root, xp);
                            }
                            //将x节点指向root进行下一次循环时跳出
                            x = root;
                        }
                    }
                } else {
                    //当x节点是右节点
                    if (xpl != null && xpl.red) {
                        //当xpl节点存在且为红色
                        //将xpl变为黑色，xp变为红色
                        xpl.red = false;
                        xp.red = true;
                        //右旋
                        root = rotateRight(root, xpl);
                        //将xpl节点重新指向xp节点的左节点
                        xpl = (xp = x.parent) == null ? null : xp.left;
                    }
                    if (xpl == null) {
                        //如果xpl节点不存在，则xp节点没有子节点了
                        //将x节点指向xp节点向上调整
                        x = xp;
                    } else {
                        //sl xpl节点的左节点
                        //sr xpl节点的右节点
                        TreeNode<K, V> sl = xpl.left, sr = xpl.right;
                        if ((sl == null || !sl.red) && (sr == null || !sr.red)) {
                            //若sr节点为空或者sr节点是黑色的，且sl节点为空或者sl节点是黑色的
                            //将xpl节点变成红色
                            xpl.red = true;
                            //则将x节点指向xp节点向上调整
                            x = xp;
                        } else {
                            //sr和sl中存在一个红节点
                            if (sl == null || !sl.red) {
                                //此处说明sr是红节点,将sr节点设置为黑色
                                sr.red = false;
                                //将xpr节点设置为红色
                                xpl.red = true;
                                //左旋
                                root = rotateLeft(root, xpl);
                                //将xpl节点重新指向xp节点的左节点
                                xpl = (xp = x.parent) == null ? null : xp.left;
                            }
                            //如果xpl节点存在
                            if (xpl != null) {
                                //使xpl节点与xp节点同色
                                xpl.red = (xp == null) ? false : xp.red;
                                //如果sl节点存在
                                if ((sl = xpl.left) != null) {
                                    //将sl节点变为黑色
                                    sl.red = false;
                                }
                            }
                            //如果xp节点存在
                            if (xp != null) {
                                //将xp节点设置为黑色
                                xp.red = false;
                                //右旋
                                root = rotateRight(root, xp);
                            }
                            //将x节点指向root进行下一次循环时跳出
                            x = root;
                        }
                    }
                }
            }
        }
```

### 3、参考

[Java 8 HashMap（九）——TreeNode的removeTreeNode和balanceDeletion](https://blog.csdn.net/weixin_48872249/article/details/115422728)



## 八、hashcode 和 System.identityHashCode

### 1、概述

hashcode 和 System.identityHashCode的区别

### 2、方法解析

```
openjdk源码:http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/5b86f66575b7
```

小例子

```java
public class TestHashCode {
    public static void main(String[] args) {
        TestHashCode test1 = new TestHashCode();
        TestHashCode test2 = new TestHashCode();
        
        System.out.println("test1.hashcode:" + test1.hashCode());
        System.out.println("test2.hashcode:" + test2.hashCode());
        
        System.out.println("test1.identityHashCode:" + System.identityHashCode(test1));
        System.out.println("test2.identityHashCode:" + System.identityHashCode(test2));
    }
}
```

输出：

```java
test1.hashcode:366712642
test2.hashcode:1829164700
test1.identityHashCode:366712642
test2.identityHashCode:1829164700
```

可以看到同一个对象调用`hashCode`方法和`identityHashCode`的结果是一样的.

稍微改动一下

```java
public class TestHashCode {
    
    public int hashCode() {
        return 3;
    }
    public static void main(String[] args) {
        TestHashCode test1 = new TestHashCode();
        TestHashCode test2 = new TestHashCode();
        
        System.out.println("test1.hashcode:" + test1.hashCode());
        System.out.println("test2.hashcode:" + test2.hashCode());
        
        System.out.println("test1.identityHashCode:" + System.identityHashCode(test1));
        System.out.println("test2.identityHashCode:" + System.identityHashCode(test2));
    }
}
```

输出：

```java
test1.hashcode:3
test2.hashcode:3
test1.identityHashCode:366712642
test2.identityHashCode:1829164700
```

可以看到如果子类重写了`hashCode`方法之后,调用自身的`hashCode`的方法时运行时多态就会调用自身的已经重写的`hashCode`方法,因此对于`hashcode`方法时结果的改变在我们的预期之内,但是对于`System.identityHashCode(Object obj)`方法没有改变?如果他们之间存在什么关系,我们需要看一下?

`identityHashCode` 和 `hashCode()`

看看`identityHashCode`的定义
 需要注意两点:

> **1.**定义里面写着不论类是否重写了`hashCode()`方法,它都会返回默认的哈希值(其实就是本地方法的值)
>  **2.**是属于`System`类里面的`static`方法

```java
 /**
     * Returns the same hash code for the given object as
     * would be returned by the default method hashCode(),
     * whether or not the given object's class overrides
     * hashCode().
     * The hash code for the null reference is zero.
     *
     * @param x object for which the hashCode is to be calculated
     * @return  the hashCode
     * @since   JDK1.1
     */
    public static native int identityHashCode(Object x);
```

对应的本地代码和[代码链接](http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/5b86f66575b7/src/share/native/java/lang/System.c)

```java
JNIEXPORT jint JNICALL
Java_java_lang_System_identityHashCode(JNIEnv *env, jobject this, jobject x)
{
 return JVM_IHashCode(env, x);
}
```

看看`hashCode()`方法
注意与`identityHashCode`的区别:

> **1.** `hashCode()`是`Object`类的方法,注意一个类都会直接或者间接继承`Object`类

```java
public native int hashCode();
```

> 对应的本地方法代码和[代码链接](http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/5b86f66575b7/src/share/native/java/lang/Object.c):



```cpp
static JNINativeMethod methods[] = {
{"hashCode",    "()I",                    (void *)&JVM_IHashCode},
{"wait",        "(J)V",                   (void *)&JVM_MonitorWait},
{"notify",      "()V",                    (void *)&JVM_MonitorNotify},
{"notifyAll",   "()V",                    (void *)&JVM_MonitorNotifyAll},
{"clone",       "()Ljava/lang/Object;",   (void *)&JVM_Clone},
};
```

> 所以从上面我们知道他们两个方法其实调用的是同一个方法`JVM_IHashCode`即可,然后再回头看一下之前的代码,我相信你一定会明白了的.

### 3、参考

[hashcode 和 System.identityHashCode](https://www.jianshu.com/p/2678119ec606)



## 九、TreeNode(红黑树)的方法

### 1、概述

在写这篇文章之前,我针对红黑树参考算法导论写了一篇文章[图解红黑树-算法导论-java实现基于HashMap1.8](https://www.jianshu.com/p/7b38abfc3298),里面的的插入和删除以及旋转就是用的HashMap1.8里面的代码,所以里面细致地分析了`balanceDeletion`,`balanceInsertion`,`rotateLeft`,`rotateRight`,那这篇主要分析`TreeNode`中去其他的方法以及一些`HashMap`中`TreeNode`新加入的属性和操作.

### 2、代码解析

#### 2.1、往红黑树中插入元素 `putTreeVal`方法

> 在看`putTreeVal`方法前,先看这几个函数,因为`putTreeVal`在函数体中有调用这几个函数

##### `comparableClassFor`方法



```jsx
 /**
     * 
     * @param x 对象x
     * @return 如果x所属的类实现了Comparable<T>接口,并且T是x类 比如 class Person implements Comparable<Person>
     *         如果class Person 或者类似于 class Person implements Comparable 或者 class Person implements Comparable<String>都会返回null
     */
    static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            Class<?> c; Type[] ts, as; Type t; ParameterizedType p;
            if ((c = x.getClass()) == String.class) // 如果是String类直接返回了
                return c;
            /**
             * getGenericInterfaces():
             *       Returns the Types representing the interfaces directly implemented
             *       by the class or interface represented by this object.
             * 就是返回c类实现的所有接口
             */
            if ((ts = c.getGenericInterfaces()) != null) {   
                for (int i = 0; i < ts.length; ++i) {
                        /**
                         *  Comparable<Person> 如果此接口含有泛型 并且原型class是Compable类
                         *  getActualTypeArguments() 返回这个类里面所有的泛型 返回一个数组
                         *  as.length == 1 && as[0] == c 是为了保证Comparable<T>里面只有一个泛型并且就是传入的类c
                         */
                    
                    if (((t = ts[i]) instanceof ParameterizedType) &&
                        ((p = (ParameterizedType)t).getRawType() ==
                         Comparable.class) &&
                        (as = p.getActualTypeArguments()) != null &&
                        as.length == 1 && as[0] == c) // type arg is c
                        return c;
                }
            }
        }
        return null;
    }
```

> 给一个最直观的测试让大家明白这个函数的作用.

| Person类的定义                               | 调用                                        | 返回     |
| -------------------------------------------- | ------------------------------------------- | -------- |
| `class Person`                               | `comparableClassFor(new Person("Tom", 12))` | `null`   |
| `class Person implements Comparable`         | `comparableClassFor(new Person("Tom", 12))` | `null`   |
| `class Person implements Comparable<String>` | `comparableClassFor(new Person("Tom", 12))` | `null`   |
| `class Person implements Comparable<Person>` | `comparableClassFor(new Person("Tom", 12))` | `Person` |

##### `compareComparables`方法

> 方法很简单,就是在满足条件的情况下调用`CompareTo`方法返回,否则返回`0`



```dart
/**
     * Returns k.compareTo(x) if x matches kc (k's screened comparable
     * class), else 0.
     * 如果类不同就返回0 否则就返回调用compareTo比较后的结果
     */
    @SuppressWarnings({"rawtypes","unchecked"}) // for cast to Comparable
    static int compareComparables(Class<?> kc, Object k, Object x) {
        return (x == null || x.getClass() != kc ? 0 :
                ((Comparable)k).compareTo(x));
    }
```

##### `tieBreakOrder`方法

> identityHashCode()和hashCode()的区别会在另外一篇博客[待完成]()中分析



```dart
      /**
         * a或者b为null或者如果a和b同属一个类就调用系统的identityHashCode
         */
        static int tieBreakOrder(Object a, Object b) {
            int d;
            if (a == null || b == null ||
                (d = a.getClass().getName().
                 compareTo(b.getClass().getName())) == 0)
                d = (System.identityHashCode(a) <= System.identityHashCode(b) ?
                     -1 : 1);
            return d;
        }
```

##### `root`方法

> 因为当前的节点可能不是红黑树的根,为什么呢?
>
> > **1.**红黑树中的每个节点都是`TreeNode`节点,所以每个节点都可以调用`TreeNode`中的方法.



```kotlin
final TreeNode<K,V> root() {
            for (TreeNode<K,V> r = this, p;;) {
                if ((p = r.parent) == null)
                    return r;
                r = p;
            }
        }
```

##### `find`方法

> `TreeNode<K,V> find(int h, Object k, Class<?> kc)`方法就是二叉树的查找了,查找该`k`和对应的`h`是否存在在以当前节点为头结点的子树中了.

> 代码就不贴了,比较简单.

##### `putTreeVal`方法

> 因为红黑树插入的时候需要比较`TreeNode`,这里是把节点的`hash`值来比较大小,具体比较机制在代码的注释中有解释.

> 至于为什么不直接用`CompareTo`方法来比较,因为在`HashMap 1.7`的时候没有引入红黑树,所以大部分的代码的`Key`是可能没有实现`Comparable`接口,因此我猜应该是为了兼容的问题.

```java
final TreeNode<K,V> putTreeVal(HashMap<K,V> map, Node<K,V>[] tab,
                                       int h, K k, V v) {
            Class<?> kc = null;
            boolean searched = false;
            //找到红黑树的根
            TreeNode<K,V> root = (parent != null) ? root() : this;
            /**
             * for 循环去寻找到该节点应该插入的位置,TreeNode之间的比较是通过该节点的hash值
             *     1. 如果该节点已经存在 那就直接返回
             *     2. 如果该节点hash值小于该节点的hash值 往左侧
             *     3. 如果该节点hash值小于该节点的hash值 往右侧
             *     
             */
            for (TreeNode<K,V> p = root;;) {
                int dir, ph; K pk;
                if ((ph = p.hash) > h) // 左侧
                    dir = -1;
                else if (ph < h)      // 右侧
                    dir = 1;
                else if ((pk = p.key) == k || (k != null && k.equals(pk))) //已经存在
                    return p;
                /**
                 * hash值相等但是key值没有匹配上会进行以下操作
                 * 1. 调用comparableClassFor先看该k是否已经实现compareable<k.class>接口
                 * 2. 如果实现了Compareable<k.class>接口 就进行compareComparables方法看看是否可以比较出结果
                 * 3. 如果还是没有比较出结果,就去左右子树中搜索有没有该节点(注意只搜索一次)
                 * 4. 如果左右子树中也没有该节点,说明必须要插入该节点到此红黑树中,所以就调用tieBreakOrder强行分出大小
                 */
                else if ((kc == null &&                                    
                          (kc = comparableClassFor(k)) == null) ||
                         (dir = compareComparables(kc, k, pk)) == 0) {
                    if (!searched) {
                        TreeNode<K,V> q, ch;
                        searched = true;
                        if (((ch = p.left) != null &&
                             (q = ch.find(h, k, kc)) != null) ||
                            ((ch = p.right) != null &&
                             (q = ch.find(h, k, kc)) != null))
                            return q;
                    }
                    dir = tieBreakOrder(k, pk);
                }
                
                /**
                 * 观察当前节点是进入左侧 还是进入右侧 还是插入
                 * 如果是插入的时候会进入if block 中的内容
                 */
                TreeNode<K,V> xp = p;
                if ((p = (dir <= 0) ? p.left : p.right) == null) {
                    Node<K,V> xpn = xp.next;
                    TreeNode<K,V> x = map.newTreeNode(h, k, v, xpn);  //生成节点
                    //插入到红黑树中
                    if (dir <= 0)
                        xp.left = x;
                    else
                        xp.right = x;
                    //插入到链表中
                    xp.next = x;
                    x.parent = x.prev = xp;
                    if (xpn != null)
                        ((TreeNode<K,V>)xpn).prev = x;
                    /**
                     * 调整红黑树返回新的节点值
                     * 把返回新的节点值移到链表的头
                     */
                    moveRootToFront(tab, balanceInsertion(root, x));
                    return null;
                }
            }
        }
```

> `balanceInsertion`方法在[图解红黑树-算法导论-java实现基于HashMap1.8](https://www.jianshu.com/p/7b38abfc3298)已经分析过了

> **由于`HashMap`在树化的过程中既保持了红黑树的结构,并且也保持了原先链表中的结构,只不过这个链表与其他没有树化的链表的区别在于树化的链表的节点类型是`TreeNode`,而没有树化的链表的节点类型是`Node`,所以当新节点在插入的时候既要往红黑树中插入,也要往链表中插入.**

> 所以在`balanceInsertion`的过程中有可能会通过旋转`root`会发生改变,因此`moveRootToFront`的作用就是把`root`节点`move`到链表的头.

```java
 static <K,V> void moveRootToFront(Node<K,V>[] tab, TreeNode<K,V> root) {
            int n;
            if (root != null && tab != null && (n = tab.length) > 0) {
                    //计算在哪个bin 
                int index = (n - 1) & root.hash;
                TreeNode<K,V> first = (TreeNode<K,V>)tab[index];
                //如果链表的头不是红黑树的头节点 则把root移到头节点 也就是first的前面
                if (root != first) {
                    Node<K,V> rn;
                    tab[index] = root;
                    TreeNode<K,V> rp = root.prev;
                    if ((rn = root.next) != null)
                        ((TreeNode<K,V>)rn).prev = rp;
                    if (rp != null)
                        rp.next = rn;
                    if (first != null)
                        first.prev = root;
                    root.next = first;
                    root.prev = null;
                }
                // 检查一下红黑树的各个成员变量是否正常
                assert checkInvariants(root);
            }
        }
```

##### `checkInvariants`方法

> 循环检查红黑树每个节点的成员变量是否正常.

```java
static <K,V> boolean checkInvariants(TreeNode<K,V> t) {
            TreeNode<K,V> tp = t.parent, tl = t.left, tr = t.right,
                tb = t.prev, tn = (TreeNode<K,V>)t.next;
            if (tb != null && tb.next != t)
                return false;
            if (tn != null && tn.prev != t)
                return false;
            if (tp != null && t != tp.left && t != tp.right)
                return false;
            if (tl != null && (tl.parent != t || tl.hash > t.hash))
                return false;
            if (tr != null && (tr.parent != t || tr.hash < t.hash))
                return false;
            if (t.red && tl != null && tl.red && tr != null && tr.red)
                return false;
            if (tl != null && !checkInvariants(tl))
                return false;
            if (tr != null && !checkInvariants(tr))
                return false;
            return true;
        }
```

#### 2.2、树化 `treeify`方法

> 调用该方法的`TreeNode`节点是一个从原先的`Node`类型的链表转化成`TreeNode`中的头节点,看原因在哪里? 在`treeifyBin`方法中可以找到答案的.

```java
/**
         * @return 调用该方法的时候this就已经是一个TreeNode了 
         *         而且整个链表的节点类型已经从Node转换成TreeNode 但是顺序没有变化
         */
        final void treeify(Node<K,V>[] tab) {
            TreeNode<K,V> root = null;
            for (TreeNode<K,V> x = this, next; x != null; x = next) {
                next = (TreeNode<K,V>)x.next;
                x.left = x.right = null;
                // 插入的是第一个元素 并给root赋值
                if (root == null) {
                    x.parent = null;
                    x.red = false;
                    root = x;
                }
                else {
                    K k = x.key;
                    int h = x.hash;
                    Class<?> kc = null;
                    //插入到红黑树中的位置 逻辑跟putTreeVal类似
                    for (TreeNode<K,V> p = root;;) {
                        int dir, ph;
                        K pk = p.key;
                        if ((ph = p.hash) > h)
                            dir = -1;
                        else if (ph < h)
                            dir = 1;
                        else if ((kc == null &&
                                  (kc = comparableClassFor(k)) == null) ||
                                 (dir = compareComparables(kc, k, pk)) == 0)
                            dir = tieBreakOrder(k, pk);

                        TreeNode<K,V> xp = p;
                        if ((p = (dir <= 0) ? p.left : p.right) == null) {
                            x.parent = xp;
                            if (dir <= 0)
                                xp.left = x;
                            else
                                xp.right = x;
                            root = balanceInsertion(root, x);
                            break;
                        }
                    }
                }
            }
            // 把root节点移到链表头
            moveRootToFront(tab, root);
        }
```

#### 2.3、链表化`untreeify`

> 当红黑树中的节点个数等于6,就把红黑树转化成简单的链表类型



```kotlin
final Node<K,V> untreeify(HashMap<K,V> map) {
            Node<K,V> hd = null, tl = null;
            for (Node<K,V> q = this; q != null; q = q.next) {
                    //将Node转化成TreeNode
                Node<K,V> p = map.replacementNode(q, null);
                if (tl == null)
                    hd = p;
                else
                    tl.next = p;
                tl = p;
            }
            return hd;
        }
```

#### 2.4、扩容时转移节点 `split`

> 思想与链表的转移节点类似,根据`rehash`的特殊性,把红黑树拆成两个链表,然后再根据两个链表的长度决定是否树化或者链表化.对原理不明白的可以参考另一篇文章[HashMap1.8 源码解析(1)--插入元素](https://www.jianshu.com/p/7fb0b940556d)的`rehash`部分.

> 有人可能对链表化有疑问？毕竟已经是链表了啊,为什么还需要进行链表化,答案是因为此时的链表是`TreeNode`类型的,需要转化成`Node`类型的.



```java
        final void split(HashMap<K,V> map, Node<K,V>[] tab, int index, int bit) {
            TreeNode<K,V> b = this;
            // 将红黑树根据rehash分成两个链表
            TreeNode<K,V> loHead = null, loTail = null;
            TreeNode<K,V> hiHead = null, hiTail = null;
            int lc = 0, hc = 0;
            for (TreeNode<K,V> e = b, next; e != null; e = next) {
                next = (TreeNode<K,V>)e.next;
                e.next = null;
                if ((e.hash & bit) == 0) {
                    if ((e.prev = loTail) == null)
                        loHead = e;
                    else
                        loTail.next = e;
                    loTail = e;
                    ++lc;
                }
                else {
                    if ((e.prev = hiTail) == null)
                        hiHead = e;
                    else
                        hiTail.next = e;
                    hiTail = e;
                    ++hc;
                }
            }
            
            //根据每个链表的长度来决定是树化还是链表化

            if (loHead != null) {
                if (lc <= UNTREEIFY_THRESHOLD)
                    tab[index] = loHead.untreeify(map);
                else {
                    tab[index] = loHead;
                    if (hiHead != null) // (else is already treeified)
                        loHead.treeify(tab);
                }
            }
            if (hiHead != null) {
                if (hc <= UNTREEIFY_THRESHOLD)
                    tab[index + bit] = hiHead.untreeify(map);
                else {
                    tab[index + bit] = hiHead;
                    if (loHead != null)
                        hiHead.treeify(tab);
                }
            }
        }
```

**1.**[HashMap1.8 源码解析(1)--插入元素](https://www.jianshu.com/p/7fb0b940556d)
 **2.**[HashMap1.8 源码解析(2)--删除元素](https://www.jianshu.com/p/6dcce5104307)
 **3.**[HashMap1.8 源码解析(3)--TreeNode(红黑树)包括每一个方法](https://www.jianshu.com/p/91215c6d061f)
 **4.**[HashMap1.8 源码解析(4)--遍历元素](https://www.jianshu.com/p/fdf4ab4d71db)

### 3、参考

[HashMap1.8 源码解析(3)--TreeNode(红黑树) 包括每一个方法](https://www.jianshu.com/p/91215c6d061f)





























## END





