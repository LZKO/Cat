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

















## END





