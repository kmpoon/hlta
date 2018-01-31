package tm.util

/**
 * We define class TreeList to avoid function calling mess
 * For example, for Seq[Tree[A]], to do .map() on every node
 *     treeSeq.map.map(f)
 * Replacing Seq[Tree[A]] with TreeList[A], now to do .map() on every node
 *     treeList.map(f)
 * And since TreeList is generic, we can do
 *     treeList.map(f).map(g).trimLevels(lvs).map(h)...
 */
class TreeList[+A](val roots: Seq[Tree[A]]){
      
  def findSubTrees(p: (A) => Boolean): TreeList[A] = new TreeList(roots.map{_.findSubTrees(p)}.flatten)
  
  def foreach(f: A => Unit): Unit = roots.foreach(_.foreach(f))
  
  def map[B](f: A => B): TreeList[B] = new TreeList(roots.map(_.map(f)))
  
  def foldLeft[B](z: B)(op: (B, A) => B): Seq[B] = roots.map(_.foldLeft(z)(op))
  
  def toList(): List[A] = roots.map(_.toList()).flatten.toList
  
  def toSeq(): Seq[A] = roots.map(_.toList()).flatten
  
  /**
   * Returns a map that contains the levels of the nodes of the trees.
   */
  def findLevels[B >: A](): Map[B, Int] = Tree.findLevels(roots)
  
  def trimLevels(takeLevels: List[Int]): TreeList[A] = new TreeList(Tree.trimLevels(roots, takeLevels))
  
  def sortRoots[B](f: Tree[A] => B)(implicit ord: Ordering[B]) = new TreeList(roots.sortBy(f))
  
}

class Tree[+A](val value: A, val children: List[Tree[A]]) {
  
  def isLeaf = children.isEmpty

  //  def foldLeft[B](z: B)(op: (B, A) => B): B = {
  //    children.foldLeft(op(z, value))((z1, t) => t.foldLeft(z1)(op))
  //  }

  def findSubTrees(p: (A) => Boolean): Seq[Tree[A]] = {
    def find(l: List[Tree[A]], t: Tree[A], p: (A) => Boolean): List[Tree[A]] = {
      val base = if (t.children.isEmpty) l
      else t.children.foldLeft(l)(find(_, _, p))

      if (p(t.value)) t :: base
      else base
    }

    find(Nil, this, p)
  }

  def getChild[B >: A](key: B) = children.find(_.value == key)
  
  def foreach(f: A => Unit): Unit = {
    f(value)
    children.foreach(_.foreach(f))
  }

  def map[B](f: (A) => B): Tree[B] = new Tree(f(value), children.map(_.map(f)))

  def foldLeft[B](z: B)(op: (B, A) => B): B = {
    val c = children.foldLeft(z)((x, t) => t.foldLeft(x)(op))
    op(c, value)
  }

  def toSeq(): Seq[A] = toList.toSeq

  def toList(): List[A] = foldLeft(List.empty[A])(_.`+:`(_)).reverse
}

object Tree {
  def node[A](value: A, children: Seq[Tree[A]]) = {
    if (children.size == 0)
      leaf(value)
    else
      new Tree(value, children.toList)
  }

  def branch[A](value: A, children: Tree[A]*) = {
    new Tree(value, children.toList)
  }

  def leaf[A](value: A) = {
    new Tree(value, Nil)
  }

  /**
   * Returns a map that contains the levels of the nodes of the trees.
   */
  def findLevels[T](trees: Seq[Tree[T]]): Map[T, Int] = {

    def findLevels[T](levels: Map[T, Int], tree: Tree[T]): Map[T, Int] = {
      if (levels.contains(tree.value)) levels
      else {
        // determine the child levels
        val updated = tree.children.foldLeft(levels)(findLevels[T])
        val childLevels = tree.children.map(c => updated(c.value))
        val level = if (childLevels.size == 0) 1 else childLevels.min + 1
        updated + (tree.value -> level)
      }
    }

    trees.foldLeft(Map.empty[T, Int])(findLevels[T])
  }
  
  def trimLevels[T](trees: Seq[Tree[T]], takeLevels: List[Int]): Seq[Tree[T]] = {
    val levelMap = findLevels(trees)
    
    def _trimLevels(tree: Tree[T]): Seq[Tree[T]] = {
      val nodeLevel = levelMap(tree.value)
      val newChildren = tree.children.flatMap(_trimLevels(_))
      if(takeLevels.contains(nodeLevel))
        Seq(Tree.node(tree.value, newChildren))
      else{
        newChildren
      }
    }
    
    trees.flatMap(_trimLevels(_))
  }
  
}