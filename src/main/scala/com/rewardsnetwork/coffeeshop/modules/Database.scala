package com.rewardsnetwork.coffeeshop.modules

import cats.effect.*
import com.rewardsnetwork.coffeeshop.config.*
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor

object Database {

  def makePostgresResource[F[_]: Async](config: PostgresConfig): Resource[F, HikariTransactor[F]] =
    for {
      ec <- ExecutionContexts.fixedThreadPool(config.nThreads)
      xa <- HikariTransactor.newHikariTransactor[F](
        "org.postgresql.Driver",
        config.url,
        config.user,
        config.pass,
        ec
      )
    } yield xa

}
