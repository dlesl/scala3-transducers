package com.dlesl.transducers

import scala.annotation.targetName
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

type Transducer[A, B] = [R] => Reducer[B, R] => Reducer[A, R]

object Transducer {
  def apply[A](): Transducer[A, A] = identity[A]

  def identity[A]: Transducer[A, A] =
    [R] => (r: Reducer[A, R]) => r

  def map[A, B](f: A => B): Transducer[A, B] =
    [R] => (rf: Reducer[B, R]) => new Reducer[A, R] :
      def init(): R = rf.init()

      def step(r: R, a: A): Result[R] = rf.step(r, f(a))

      def completion(r: R): R = rf.completion(r)

  def filter[A](pred: A => Boolean): Transducer[A, A] =
    [R] => (rf: Reducer[A, R]) => new Reducer[A, R] :
      def init(): R = rf.init()

      def step(r: R, a: A): Result[R] = if pred(a) then rf.step(r, a) else Result(r)

      def completion(r: R): R = rf.completion(r)

  def collect[A, B](pf: PartialFunction[A, B]): Transducer[A, B] =
    [R] => (rf: Reducer[B, R]) => new Reducer[A, R] :
      def init(): R = rf.init()

      def step(r: R, a: A): Result[R] = if pf.isDefinedAt(a) then rf.step(r, pf(a)) else Result(r)

      def completion(r: R): R = rf.completion(r)

  def take[A](n: Int): Transducer[A, A] =
    [R] => (rf: Reducer[A, R]) =>
      var remaining = n
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          val res = if remaining > 0 then rf.step(r, a) else Result(r)
          remaining -= 1
          if remaining <= 0 then res.stop else res

        def completion(r: R): R = rf.completion(r)

  def drop[A](n: Int): Transducer[A, A] =
    [R] => (rf: Reducer[A, R]) =>
      var remaining = n
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          if remaining > 0 then
            remaining -= 1
            Result(r)
          else
            rf.step(r, a)

        def completion(r: R): R = rf.completion(r)


  def distinct[A](classifier: A => Any = Predef.identity): Transducer[A, A] =
    [R] => (rf: Reducer[A, R]) =>
      val seen = mutable.HashSet[Any]()
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          if seen.add(classifier(a)) then rf.step(r, a) else Result(r)

        def completion(r: R): R = rf.completion(r)

  def takeWhile[A](pred: A => Boolean): Transducer[A, A] =
    [R] => (rf: Reducer[A, R]) =>
      var taking = true
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          if taking && !pred(a) then taking = false
          if taking then rf.step(r, a) else Result(r, false)

        def completion(r: R): R = rf.completion(r)

  def dropWhile[A](pred: A => Boolean): Transducer[A, A] =
    [R] => (rf: Reducer[A, R]) =>
      var dropping = true
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          if dropping && !pred(a) then dropping = false
          if dropping then Result(r) else rf.step(r, a)

        def completion(r: R): R = rf.completion(r)

  def interpose[A](sep: A): Transducer[A, A] =
    [R] => (rf: Reducer[A, R]) =>
      var started = false
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          if started then
            val res = rf.step(r, sep)
            if res.continue then rf.step(res.value, a) else res
          else
            started = true
            rf.step(r, a)

        def completion(r: R): R = rf.completion(r)

  def partitionBy[A](f: A => Any): Transducer[A, Seq[A]] =
    [R] => (rf: Reducer[Seq[A], R]) =>
      var next = ArrayBuffer.empty[A]
      var lastSeen: Option[Any] = None
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          val thisSeen = f(a)
          if lastSeen.contains(thisSeen) then
            next.append(a)
            Result(r)
          else if lastSeen.isEmpty then
            lastSeen = Some(thisSeen)
            next.append(a)
            Result(r)
          else
            lastSeen = Some(thisSeen)
            val nextSeq = next.toSeq
            next.clear()
            val res = rf.step(r, nextSeq)
            if res.continue then next.append(a)
            res

        def completion(r: R): R =
          val res = if next.isEmpty then
            r
          else
            val nextSeq = next.toSeq
            next.clear()
            rf.step(r, nextSeq).value
          rf.completion(res)

  def indexed[A]: Transducer[A, (Int, A)] =
    [R] => (rf: Reducer[(Int, A), R]) =>
      var i = -1
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          i += 1
          rf.step(r, (i, a))

        def completion(r: R): R = rf.completion(r)

  def flatten[A]: Transducer[IterableOnce[A], A] =
    [R] => (rf: Reducer[A, R]) =>
      new Reducer[IterableOnce[A], R] :
        def init(): R = rf.init()

        def step(r: R, a: IterableOnce[A]): Result[R] =
          val it = a.iterator
          if (!it.hasNext) return Result(r)
          var res = rf.step(r, it.next())
          while (res.continue && it.hasNext) {
            res = rf.step(res.value, it.next())
          }
          res

        def completion(r: R): R = rf.completion(r)

  def flatMap[A, B](f: A => IterableOnce[B]): Transducer[A, B] = map(f).flatten

  def count[A]: Transducer[A, Int] =
    [R] => (rf: Reducer[Int, R]) =>
      var n = 0
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          n += 1
          Result(r)

        def completion(r: R): R =
          rf.completion(rf.step(r, n).value)

  def count[A, B](classifier: A => B): Transducer[A, Map[B, Int]] =
    [R] => (rf: Reducer[Map[B, Int], R]) =>
      val counts = mutable.Map.empty[B, Int]
      new Reducer[A, R] :
        def init(): R = rf.init()

        def step(r: R, a: A): Result[R] =
          val k = classifier(a)
          counts.put(k, counts.getOrElse(k, 0) + 1)
          Result(r)

        def completion(r: R): R =
          rf.completion(rf.step(r, counts.toMap).value)
}

extension[A, B] (f: Transducer[A, B])
  def compose[C](g: Transducer[B, C]): Transducer[A, C] =
    [R] => (reducer: Reducer[C, R]) => f(g(reducer))

  def >>[C](g: Transducer[B, C]): Transducer[A, C] = compose(g)

  def andThen[C](g: Transducer[C, A]): Transducer[C, B] =
    [R] => (reducer: Reducer[B, R]) => g(f(reducer))

  def map[C](g: B => C): Transducer[A, C] = f >> Transducer.map(g)

  def flatMap[C](g: B => IterableOnce[C]): Transducer[A, C] = f >> Transducer.flatMap(g)

  def filter(pred: B => Boolean): Transducer[A, B] = f >> Transducer.filter[B](pred)

  def collect[C](pf: PartialFunction[B, C]) = f >> Transducer.collect(pf)

  def take(n: Int): Transducer[A, B] = f >> Transducer.take(n)

  def takeWhile(pred: B => Boolean): Transducer[A, B] = f >> Transducer.takeWhile(pred)

  def dropWhile(pred: B => Boolean): Transducer[A, B] = f >> Transducer.dropWhile(pred)

  def distinct(classifier: B => Any = Predef.identity): Transducer[A, B] = f >> Transducer.distinct(classifier)

  def interpose(sep: B): Transducer[A, B] = f >> Transducer.interpose(sep)

  def partitionBy(g: B => Any): Transducer[A, Seq[B]] = f >> Transducer.partitionBy[B](g)

  def indexed: Transducer[A, (Int, B)] = f >> Transducer.indexed

  def count: Transducer[A, Int] = f >> Transducer.count

  def count[C](classifier: B => C): Transducer[A, Map[C, Int]] = f >> Transducer.count(classifier)

extension[A, B] (f: Transducer[A, IterableOnce[B]])
  def flatten: Transducer[A, B] = f >> Transducer.flatten

extension[A, B] (input: IterableOnce[A])
  def transduce(xf: Transducer[A, B]): Iterator[B] =
    val it = input.iterator
    var continue = true
    // A single A may explode into multiple Bs, so we need somewhere to store them
    val q = mutable.Queue.empty[B]
    val unitRes = Result(())
    val queueReducer = Reducer[B, Unit]((), (_, a) => {
      q.append(a);
      unitRes
    })
    val rf = xf(queueReducer)
    rf.init()

    def run(): Unit =
      while (q.isEmpty && it.hasNext && continue) {
        val res = rf.step((), it.next())
        if !res.continue then
          continue = false
          rf.completion(())
      }

    run()

    new Iterator[B] :
      def hasNext: Boolean = q.nonEmpty

      def next(): B =
        val res = q.removeHead()
        if q.isEmpty then run()
        res

