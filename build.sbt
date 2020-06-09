name := "InsuranceWidetable"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.0.0" % "provided"

libraryDependencies += "com.hankcs" % "hanlp" % "portable-1.2.11"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql"  % "2.0.0" % "provided",
  "org.apache.spark" %% "spark-mllib" % "2.0.0" % "provided"
)