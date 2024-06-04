package com.rewardsnetwork.coffeeshop.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*

case class AppConfig(
    emberConfig: EmberConfig,
    postgresConfig: PostgresConfig
) derives ConfigReader
