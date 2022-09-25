ThisBuild / organization := "com.dlesl"
ThisBuild / scalaVersion := "3.2.0"

lazy val transducers = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("transducers"))
  .settings(
    name := "transducers",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0-M6" % Test
  )