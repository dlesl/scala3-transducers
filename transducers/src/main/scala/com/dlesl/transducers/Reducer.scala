package com.dlesl.transducers

import scala.collection.mutable.ArrayBuffer

trait Reducer[A, R]:
  def init(): R

  def step(result: R, input: A): Result[R]

  def completion(result: R): R


object Reducer {
  def apply[A, R](initFn: => R, stepFn: (R, A) => Result[R], completionFn: R => R = identity[R]): Reducer[A, R] =
    new Reducer[A, R] :
      def init(): R = initFn

      def step(result: R, input: A): Result[R] = stepFn(result, input)

      def completion(result: R): R = completionFn(result)

  def simple[A, R](initFn: => R, stepFn: (R, A) => R, completionFn: R => R = identity[R]): Reducer[A, R] =
    apply(initFn, (r, a) => Result(stepFn(r, a)), completionFn)

  def toSeq[A]: Reducer[A, Seq[A]] =
    val res = ArrayBuffer.empty[A]
    val incomplete = Result[Seq[A]](Nil)
    Reducer[A, Seq[A]](Nil, (_, a) => {
      res.append(a); incomplete
    }, _ => res.toSeq)
}

extension[A, R] (rf: Reducer[A, R])
  def reduce(seq: IterableOnce[A]): R =
    val it = seq.iterator
    var res = Result(rf.init())
    while (res.continue && it.hasNext) {
      res = rf.step(res.value, it.next());
    }
    rf.completion(res.value)
