package com.rewardsnetwork.coffeeshop

import cats.*
import cats.implicits.*
import cats.effect.*

import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderException

import com.rewardsnetwork.coffeeshop.config.*
import com.rewardsnetwork.coffeeshop.config.syntax.*
import com.rewardsnetwork.coffeeshop.modules.{Core, Database, HttpApi}

object Application extends IOApp.Simple {

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = ConfigSource.default.loadF[IO, AppConfig].flatMap {
    case AppConfig(emberConfig, postgresConfig) =>
      val appResource = for {
        xa      <- Database.makePostgresResource[IO](postgresConfig)
        core    <- Core[IO](xa)
        httpApi <- HttpApi[IO](core)
        server <- EmberServerBuilder
          .default[IO]
          .withHost(emberConfig.host)
          .withPort(emberConfig.port)
          .withHttpApp(httpApi.endpoints.orNotFound)
          .build
      } yield server

      appResource.use(_ => IO.println("Server ready!") *> IO.never)
  }

}
