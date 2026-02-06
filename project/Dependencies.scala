import sbt._

object Dependencies {
  object Versions {
    val pureConfig    = "0.17.8"
    val circe         = "0.13.0"
    val http4s        = "0.21.22"
    val mockito       = "1.16.37"
    val refined       = "0.9.24"
    val newtype       = "0.4.4"
    val redis4cats    = "0.13.1"
    val skunk         = "0.0.26"
    val squants       = "1.8.0"
    val logback       = "1.2.3"
    val log4cats      = "1.3.0"
    val http4sJwtAuth = "0.0.6"
    val bcrypt        = "4.3.0"
    val betterFiles   = "3.9.2"
    val scalikejdbc   = "3.5.0"
    val akka          = "2.6.20"

    val scalaCheck     = "1.15.3"
    val scalaTest      = "3.2.8"
    val scalaTestPlus  = "3.2.2.0"
    val testContainers = "0.39.4"
  }

  object Libraries {
    def circe(artifact: String): ModuleID          = "io.circe"              %% artifact % Versions.circe
    def pureConfig(artifact: String): ModuleID     = "com.github.pureconfig" %% artifact % Versions.pureConfig
    def http4s(artifact: String): ModuleID         = "org.http4s"            %% artifact % Versions.http4s
    def mockito(artifact: String): ModuleID        = "org.mockito"           %% artifact % Versions.mockito
    def refined(artifact: String): ModuleID        = "eu.timepit"            %% artifact % Versions.refined
    def redis(artifact: String): ModuleID          = "dev.profunktor"        %% artifact % Versions.redis4cats
    def skunk(artifact: String): ModuleID          = "org.tpolecat"          %% artifact % Versions.skunk
    def testContainers(artifact: String): ModuleID = "com.dimafeng"          %% artifact % Versions.testContainers

    val catsTestkit = "org.typelevel"    %% "cats-testkit-scalatest" % "1.0.1"

    val pureConfigCore = pureConfig("pureconfig")
    val pureConfigCats = "com.github.pureconfig" %% "pureconfig-cats-effect2" % "0.17.8"

    val skunkCore  = skunk("skunk-core")
    val skunkCirce = skunk("skunk-circe")

    val circeCore          = circe("circe-core")
    val circeGeneric       = circe("circe-generic")
    val circeGenericExtras = circe("circe-generic-extras")
    val circeParser        = circe("circe-parser")
    val circeRefined       = circe("circe-refined")
    val circeLiteral       = circe("circe-literal")

    val http4sCore    = http4s("http4s-core")
    val http4sDsl     = http4s("http4s-dsl")
    val http4sServer  = http4s("http4s-blaze-server")
    val http4sClient  = http4s("http4s-blaze-client")
    val http4sCirce   = http4s("http4s-circe")
    val http4sJwtAuth = "dev.profunktor"    %% "http4s-jwt-auth" % Versions.http4sJwtAuth
    val bcrypt        = "com.github.t3hnar" %% "scala-bcrypt"    % Versions.bcrypt

    val refinedCore = refined("refined")
    val refinedCats = refined("refined-cats")
    val newtype     = "io.estatico" %% "newtype" % Versions.newtype

    val redis4catsCore   = redis("redis4cats-effects")
    val redis4catsStream = redis("redis4cats-streams")
    val redis4catsLogs   = redis("redis4cats-log4cats")

    val logback  = "ch.qos.logback" % "logback-classic" % Versions.logback
    val log4cats = "org.typelevel" %% "log4cats-slf4j"  % Versions.log4cats
    val squants  = "org.typelevel" %% "squants"         % Versions.squants

    val betterFiles    = "com.github.pathikrit" %% "better-files"       % Versions.betterFiles
    val scalikejdbc    = "org.scalikejdbc"      %% "scalikejdbc"        % Versions.scalikejdbc
    val akkaActor      = "com.typesafe.akka"    %% "akka-actor"         % Versions.akka
    val unboundidLdap  = "com.unboundid"        %  "unboundid-ldapsdk"  % "6.0.8"
    val h2Database     = "com.h2database"       %  "h2"                 % "2.2.224"

    val scalaCheck    = "org.scalacheck"    %% "scalacheck"      % Versions.scalaCheck
    val scalaTest     = "org.scalatest"     %% "scalatest"       % Versions.scalaTest
    val scalaTestPlus = "org.scalatestplus" %% "scalacheck-1-14" % Versions.scalaTestPlus

    val catsEffectTest   = "com.codecommit"     %% "cats-effect-testing-scalatest" % "0.4.0"
    val mockitoCore      = mockito("mockito-scala")
    val mockitoScalatest = mockito("mockito-scala-scalatest")
    val redisEmbedded    = "com.github.sebruck" %% "scalatest-embedded-redis"      % "0.4.0"

    val postgresDriver          = "org.postgresql" % "postgresql" % "42.6.0"
    val testContainersScalatest = testContainers("testcontainers-scala-scalatest")
    val testContainersPostgres  = testContainers("testcontainers-scala-postgresql")

    val scalaCompiler = "org.scala-lang" % "scala-compiler" % "2.13.14"
    val scalaReflect  = "org.scala-lang" % "scala-reflect"  % "2.13.14"
  }

  lazy val core: Seq[ModuleID] = Seq(
    Libraries.pureConfigCore,
    Libraries.pureConfigCats,
    Libraries.logback % Runtime,
    Libraries.log4cats,
    Libraries.circeCore,
    Libraries.circeGeneric,
    Libraries.circeGenericExtras,
    Libraries.circeParser,
    Libraries.circeLiteral,
    Libraries.circeRefined,
    Libraries.http4sCore,
    Libraries.http4sDsl,
    Libraries.http4sServer,
    Libraries.http4sClient,
    Libraries.http4sCirce,
    Libraries.http4sJwtAuth,
    Libraries.bcrypt,
    Libraries.squants,
    Libraries.redis4catsCore,
    Libraries.redis4catsStream,
    Libraries.redis4catsLogs,
    Libraries.refinedCore,
    Libraries.refinedCats,
    Libraries.newtype,
    Libraries.skunkCore,
    Libraries.skunkCirce,
    Libraries.betterFiles,
    Libraries.scalikejdbc,
    Libraries.akkaActor,
    Libraries.unboundidLdap,
    Libraries.h2Database,
    Libraries.postgresDriver,
    Libraries.scalaCompiler,
    Libraries.scalaReflect
  )

  lazy val test: Seq[ModuleID] = Seq(
    Libraries.scalaTest               % Test,
    Libraries.scalaTestPlus           % Test,
    Libraries.scalaCheck              % Test,
    Libraries.catsTestkit             % Test,
    Libraries.catsEffectTest          % Test,
    Libraries.mockitoCore             % Test,
    Libraries.mockitoScalatest        % Test,
    Libraries.redisEmbedded           % Test,
    Libraries.testContainersScalatest % Test,
    Libraries.testContainersPostgres  % Test,
    Libraries.postgresDriver          % Test
  )
}
