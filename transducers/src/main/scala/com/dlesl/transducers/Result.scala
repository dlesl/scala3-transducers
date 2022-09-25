package com.dlesl.transducers

case class Result[A](value: A, continue: Boolean = true):
  def stop: Result[A] = copy(continue = false)
