package com.rewardsnetwork.coffeeshop.fixtures

import cats.effect.IO

import com.rewardsnetwork.coffeeshop.algebras.Users
import com.rewardsnetwork.coffeeshop.domain.user.*

trait UserFixture {

  val Sam = User(
    "sam@coffeeshop.com",
    "$2a$10$QtmB49k67mjHdESLTjqYIeE3.h2BwmInrUTwx9Roz8L/v58v21v9W",
    Some("Sam"),
    Some("Metcalfe"),
    Role.ADMIN
  )
  val samEmail    = Sam.email
  val samPassword = "supersecure1@!"

  val John = User(
    "john@coffeeshop.com",
    "$2a$10$a1npC/0GTqevldGQECrrj.FLwSZvZP96cwkcYTMsGe2J2TSkHwVTS",
    Some("John"),
    Some("Shaw"),
    Role.SERVER
  )
  val johnEmail    = John.email
  val johnPassword = "johncoffee1!"

  val NewUser = User(
    "newuser@coffeeshop.com",
    "$2a$10$6LQt4xy4LzqQihZiRZGG0eeeDwDCvyvthICXzPKQDQA3C47LtrQFy",
    Some("Jane"),
    Some("Doe"),
    Role.SERVER
  )

  val UpdatedJohn = User(
    "john@coffeeshop.com",
    "$2a$10$a1npC/0GTqevldGQECrrj.FLwSZvZP96cwkcYTMsGe2J2TSkHwVTS",
    Some("JOHN"),
    Some("SHAW"),
    Role.SERVER
  )

  val NewUserSam = NewUserInfo(
    samEmail,
    samPassword,
    Some("Sam"),
    Some("Metcalfe"),
  )

  val NewUserJohn = NewUserInfo(
    johnEmail,
    johnPassword,
    Some("John"),
    Some("Shaw"),
  )
}