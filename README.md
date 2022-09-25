## scala3-transducers

### Experiments with transducers for Scala 3 (JVM, JS, Native)

This is a Scala version of [Clojure's
transducers](https://clojure.org/reference/transducers). They are implemented in
much the same way and therefore should have similar performance and other
properties.

The main difference is that instead of using multiple arities, reducing
functions are defined as a trait:

```scala
case class Result[A](value: A, continue: Boolean = true)

trait Reducer[A, R]:
    def init(): R
    def step(result: R, input: A): Result[R]
    def completion(result: R): R
```

`Result` is analogous to Clojure's `reduced`. 

A Transducer takes a reducing function and returns a new reducing function. As
in Clojure, it is a function.

```scala
type Transducer[A, B] = [R] => Reducer[B, R] => Reducer[A, R]
```

`[R] =>` in the type signature means that this holds for any possible type `R`,
in other words that a transducer is independent of the final result's type.

Note also that the type arguments are swapped: A `Transducer[A, B]` converts a
`Reducer[B, R]` to a `Reducer[A, R]`. This is done so that the order of the type
parameters reflects the flow of the data, which is hopefully more intuitive. For
example, the type of `Transducer.map((a: Int) => a.toString)` is 
`Transducer[Int, String]`.

### Example

```scala
import com.dlesl.transducers.*

val xf = Transducer
  .map[Int, Int](_ + 1)
  .map(_.toString)
  .filter(_.length < 2)
  .map(Integer.parseInt)

val toVector = Reducer.simple[Int, Vector[Int]](Vector.empty, _:+_)
toVector.reduce(Seq(1, 2, 3)) // Vector(1, 2, 3)
xf(toVector).reduce(Seq(1, 2, 3)) // Vector(2, 3, 4)

val sum = Reducer.simple[Int, Int](0, _+_)
xf(sum).reduce(Seq(1, 2, 3)) // 9
```

### Notes

* [transducers-scala](https://github.com/knutwalker/transducers-scala) is very
  similar (and more complete), but I decided to write my own anyway as an
  exercise in learning Scala :). It has a neat `into` implementation and a
  different way of representing the "reduced" state (an `AtomicBoolean` passed
  into the step function).
  
* The clojure implementation represents the reduced state by [wrapping the
  value](https://github.com/clojure/clojure/blob/2b3ba822815981e7f76907a3b75e9ecc428f7656/src/clj/clojure/core.clj#L2853).
  This is potentially more performant than wrapping every value in `Result` as
  done here, given that unreduced values will be vastly more common. This might
  be worth investigating (eg. returning `A | Reduced[A]`). TODO: benchmarks
  
* [This Haskell sketch of
  transducers](https://github.com/FranklinChen/clojure-transducers-in-haskell/blob/master/Transducers.hs)
  based on code by Rich Hickey helped me understand the concept using types.

### TODO (maybe)

* Investigate and compare to `fs2` and `ZIO` streams
* Benchmarks
* Feature parity with `clojure.core`
