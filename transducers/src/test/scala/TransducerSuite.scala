import com.dlesl.transducers.Reducer.toSeq
import com.dlesl.transducers.*

class TransducerSuite extends munit.FunSuite {

  test("simple reducer") {
    assertEquals(toSeq.reduce(List(1, 2, 3)), Seq(1, 2, 3))
  }

  test("mapping") {
    val xf = Transducer.map[Int, Int](_ + 1)
    assertEquals(xf(toSeq).reduce(List(1, 2, 3)), Seq(2, 3, 4))
  }

  test("filtering") {
    val xf = Transducer.filter[Int](_ % 2 == 0)
    assertEquals(xf(toSeq).reduce(List(1, 2, 3)), Seq(2))
  }

  test("filtermap") {
    val xf = Transducer.map[Int, Int](_ + 1)
      .filter(_ % 2 == 0)

    assertEquals(xf(toSeq).reduce(List(1, 2, 3)), Seq(2, 4))
  }

  test("changing types") {
    val xf = Transducer.map[Int, Int](_ + 1)
    val xf1: Transducer[Int, String] = xf >> Transducer.map[Int, String](int => int.toString)
    assertEquals(xf1(toSeq).reduce(List(1, 2, 3)), Seq("2", "3", "4"))

    val xf2: Transducer[Int, Int] = xf1 >> Transducer.map[String, Int](Integer.parseInt)
    assertEquals(xf2(toSeq).reduce(List(1, 2, 3)), Seq(2, 3, 4))
  }

  test("changing types using extensions") {
    val xf3 = Transducer.identity[Int]
      .map(_ + 1)
      .map(_.toString)
      .filter(_.length < 2)
      .map(Integer.parseInt)

    assertEquals(xf3(toSeq).reduce(List(1, 2, 3, 9)), Seq(2, 3, 4))
  }

  test("folding transducers") {
    val xfs = LazyList.fill(2)(Transducer.map[Int, Int](_ + 1))
    val xf = xfs.foldLeft[Transducer[Int, Int]](Transducer.identity)(_.compose(_))
    assertEquals(xf(toSeq).reduce(List(1)), Seq(3))
  }

  test("take") {
    assertEquals(Transducer.take(0)(toSeq).reduce(List(1, 2, 3)), Seq.empty)
    assertEquals(Transducer.take(1)(toSeq).reduce(List(1, 2, 3)), Seq(1))
    assertEquals(Transducer.take(3)(toSeq).reduce(List(1, 2, 3)), Seq(1, 2, 3))
    assertEquals(Transducer.take(4)(toSeq).reduce(List(1, 2, 3)), Seq(1, 2, 3))
    assertEquals(Transducer.take(4)(toSeq).reduce(LazyList.from(1)), Seq(1, 2, 3, 4))
  }

  test("drop") {
    assertEquals(Transducer.drop(0)(toSeq).reduce(List(1, 2, 3)), Seq(1, 2, 3))
    assertEquals(Transducer.drop(1)(toSeq).reduce(List(1, 2, 3)), Seq(2, 3))
    assertEquals(Transducer.drop(3)(toSeq).reduce(List(1, 2, 3)), Seq.empty)
    assertEquals(Transducer.drop(4)(toSeq).reduce(List(1, 2, 3)), Seq.empty)
  }

  test("takeWhile") {
    assertEquals(Transducer.takeWhile[Int](_ % 2 == 0)(toSeq).reduce(List(2, 2, 3, 2)), Seq(2, 2))
  }

  test("dropWhile") {
    assertEquals(Transducer.dropWhile[Int](_ % 2 == 0)(toSeq).reduce(List(2, 2, 3, 2)), Seq(3, 2))
  }

  test("dedup") {
    assertEquals(Transducer.distinct()(toSeq).reduce(List(1, 2, 1)), Seq(1, 2))
  }

  test("interpose") {
    val xf = Transducer.map[Int, String](_.toString) >> Transducer.interpose(",")
    assertEquals(xf(toSeq).reduce(List(1, 2, 3)).mkString, "1,2,3")
  }

  test("partitionBy") {
    val xf = Transducer.partitionBy[Int](_ / 10)
    assertEquals(xf(toSeq).reduce(List(10, 20, 30)), Seq(Seq(10), Seq(20), Seq(30)))
    assertEquals(xf(toSeq).reduce(List(10, 11, 21, 20, 30)), Seq(Seq(10, 11), Seq(21, 20), Seq(30)))
  }

  test("flatten") {
    val xf = Transducer.flatten[Int]
    assertEquals(xf(toSeq).reduce(List(List(1, 2), List(3))), Seq(1, 2, 3))
  }

  test("flatMap") {
    val xf = Transducer.flatMap(a => List(a, a))
    assertEquals(xf(toSeq).reduce(List(1)), Seq(1, 1))

    val xf2 = Transducer.identity.flatMap(a => List(a, a))
    assertEquals(xf2(toSeq).reduce(List(1)), Seq(1, 1))
  }

  test("count") {
    val xf = Transducer.count[Any]
    assertEquals(xf(toSeq).reduce(List(1, 2, 3)), Seq(3))
  }

  test("count (by)") {
    val xf = Transducer.map[Int, String](_.toString) >> Transducer.count[String, Int](_.length)
    assertEquals(xf(toSeq).reduce(List(1, 2, 30)), Seq(Map(1 -> 2, 2 -> 1)))
  }

  test("indexed") {
    val xf = Transducer.indexed.map(_._1)
    assertEquals(xf(toSeq).reduce(List(1, 2, 3)), Seq(0, 1, 2))
  }

  test("iterate") {
    val xf = Transducer.map[Int, Int](_ + 1)
    val it = LazyList.from(0).iterator.transduce(xf)
    assertEquals(LazyList.from(it).take(3), Seq(1, 2, 3))
  }

  test("iterate to end") {
    val xf = Transducer.take[Int](3)
    val it = LazyList.from(1).iterator.transduce(xf)
    assertEquals(LazyList.from(it).take(4), Seq(1, 2, 3))
  }

  test("iterate empty list") {
    val xf = Transducer.take[Int](0)
    val it = LazyList.from(1).iterator.transduce(xf)
    assertEquals(LazyList.from(it).take(4), Seq.empty)
  }

  test("iterate empty steps") {
    val xf = Transducer.flatten[Int]
    val it = Seq(Seq.empty[Int], Seq.empty[Int], Seq.empty[Int], Seq(1)).iterator.transduce(xf)
    assertEquals(LazyList.from(it), Seq(1))
  }

  test("iterate chunky") {
    val xf = Transducer.flatMap[Int, Int](a => Seq.range(a, a + 2)).take(7)
    val it = LazyList.from(1).iterator.transduce(xf)
    assertEquals(LazyList.from(it), Seq(1, 2, 2, 3, 3, 4, 4))
  }
}
