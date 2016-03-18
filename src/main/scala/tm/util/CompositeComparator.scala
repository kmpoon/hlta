package tm.util

import scala.annotation.tailrec

/**
 * Used to construct a composite comparator consisting of multiple comparator.
 */
object CompositeComparator {
    def apply[T](comparators: (T, T) => Int*): (T, T) => Int = {
        @tailrec
        def rec(o1: T, o2: T, cs: Seq[(T, T) => Int]): Int = cs match {
            case Nil => 0
            case head +: tail => {
                val compare = head(o1, o2)
                if (compare == 0)
                    rec(o1, o2, tail)
                else
                    compare
            }
        }

        (o1, o2) => rec(o1, o2, comparators)
    }
}