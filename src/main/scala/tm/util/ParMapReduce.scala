package tm.util

import scala.collection.parallel.ParIterable

/**
 * A map reduce function for parallel collection.  This function provides
 * a non-strict view on the collection after the map function.  It is necessary
 * because the standard parallel collection calls the map function before 
 * the reduce function is called.
 */
object ParMapReduce {
  def mapReduce[T, U](xs: ParIterable[T])(m: (T) => U)(r: (U, U) => U): U = {
    trait O { def toA: A }
    class S(val x: T) extends O {
      def toA = new A(m(x))
    }
    class A(val a: U) extends O {
      def toA = this
      def +(b: A): A = new A(r(a, b.a))
    }

    xs.map(new S(_): O).reduce(_.toA + _.toA).toA.a
  }
}