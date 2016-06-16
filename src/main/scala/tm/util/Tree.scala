package tm.util

sealed case class Tree[+A](value: A, children: List[Tree[A]]) {
  def isLeaf = children.isEmpty

  //  def foldLeft[B](z: B)(op: (B, A) => B): B = {
  //    children.foldLeft(op(z, value))((z1, t) => t.foldLeft(z1)(op))
  //  }

  def findSubTrees(p: (A) => Boolean): List[Tree[A]] = {
    def find(l: List[Tree[A]], t: Tree[A], p: (A) => Boolean): List[Tree[A]] = {
      val base = if (t.children.isEmpty) l
      else t.children.foldLeft(l)(find(_, _, p))

      if (p(t.value)) t :: base
      else base
    }

    find(Nil, this, p)
  }

  def getChild[B >: A](key: B) = children.find(_.value == key)

  def map[B](f: (A) => B): Tree[B] = {
    Tree(f(value), children.map(_.map(f)))
  }

  def foldLeft[B](z: B)(op: (B, A) => B): B = {
    val c = children.foldLeft(z)((x, t) => t.foldLeft(x)(op))
    op(c, value)
  }

  def toList(): List[A] = foldLeft(List.empty[A])(_ :+ _)
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
}
