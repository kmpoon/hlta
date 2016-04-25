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
}

object Tree {
  def node[A](value: A, children: Tree[A]*) = {
    new Tree(value, children.toList)
  }

  def leaf[A](value: A) = {
    new Tree(value, Nil)
  }
}
