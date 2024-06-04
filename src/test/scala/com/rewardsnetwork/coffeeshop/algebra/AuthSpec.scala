package com.rewardsnetwork.coffeeshop.algebra

import cats.effect.*
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.*
import cats.data.*

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import tsec.authentication.IdentityStore
import tsec.authentication.JWTAuthenticator
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

import scala.concurrent.duration.*

import com.rewardsnetwork.coffeeshop.fixtures.*
import com.rewardsnetwork.coffeeshop.domain.user.*
import com.rewardsnetwork.coffeeshop.domain.security.*
import com.rewardsnetwork.coffeeshop.algebras.*
import com.rewardsnetwork.coffeeshop.domain.auth.*
import com.rewardsnetwork.coffeeshop.domain.security.Authenticator

class AuthSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers with UserFixture {

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val mockedUsers: Users[IO] = new Users[IO] {
    def find(email: String): IO[Option[User]] =
      if (email == samEmail) IO.pure(Some(Sam))
      else IO.pure(None)
    def create(user: User): IO[String]       = IO.pure(user.email)
    def update(user: User): IO[Option[User]] = IO.pure(Some(user))
    def delete(email: String): IO[Boolean]   = IO.pure(true)
  }

  val mockedAuthenticator: Authenticator[IO] = {
    // hashing key
    val key = HMACSHA256.unsafeGenerateKey
    // identity store to retrieve users
    val idStore: IdentityStore[IO, String, User] = (email: String) =>
      if (email == samEmail) OptionT.pure(Sam)
      else if (email == johnEmail) OptionT.pure(John)
      else OptionT.none[IO, User]
    JWTAuthenticator.unbacked.inBearerToken(
      1.day,   // expiration
      None,    // idle
      idStore,
      key      // hash key
    )
  }

  "Auth 'algebra'" - {
    "login should return None when the user doesn't exist" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        tokenOpt <- auth.login("idontexist@coffeeshop.com", "password")
      } yield tokenOpt

      program.asserting(_ shouldBe None)
    }

    "login should return None if the user exists but the password is wrong" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        tokenOpt <- auth.login(samEmail, "wrongPassword")
      } yield tokenOpt

      program.asserting(_ shouldBe None)
    }

    "login should return a token if user and password correct and user exists" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        tokenOpt <- auth.login(samEmail, samPassword)
      } yield tokenOpt

      program.asserting(_ shouldBe defined)
    }

    "signup should not create a user when signing up with an existing email" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        tokenOpt <- auth.signUp(
          NewUserInfo(
            samEmail,
            "somePassword",
            Some("Sam"),
            Some("Metcalfe")
          )
        )
      } yield tokenOpt

      program.asserting(_ shouldBe None)
    }

    "signup should create a new user" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        tokenOpt <- auth.signUp(
          NewUserInfo(
            "tim@coffeeshop.com",
            "somePassword",
            Some("Tim"),
            Some("Tiny")
          )
        )
      } yield tokenOpt

      program.asserting {
        case Some(user) =>
          user.email shouldBe "tim@coffeeshop.com"
          user.firstName shouldBe Some("Tim")
          user.lastName shouldBe Some("Tiny")
          user.role shouldBe Role.SERVER
        case _ =>
          fail()
      }
    }

    "changePassword should return a Right(None) if user doesn't exist" in {
      val program = for {
        auth   <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        result <- auth.changePassword("lynda@coffeeshop.com", NewPasswordInfo("oldpw", "newpw"))
      } yield result

      program.asserting(_ shouldBe Right(None))
    }

    "changePassword should return a Left(error) if password is incorrect" in {
      val program = for {
        auth   <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        result <- auth.changePassword(samEmail, NewPasswordInfo("oldpw", "newpw"))
      } yield result

      program.asserting(_ shouldBe Left("Invalid Password"))
    }

    "changePassword should change password if details are correct" in {
      val program = for {
        auth   <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        result <- auth.changePassword(samEmail, NewPasswordInfo(samPassword, "mynewsecurepass"))
        isCorrectPassword <- result match {
          case Right(Some(user)) =>
            BCrypt.checkpwBool[IO]("mynewsecurepass", PasswordHash[BCrypt](user.hashedPassword))
          case _ =>
            IO.pure(false)
        }
      } yield isCorrectPassword

      program.asserting(_ shouldBe true)
    }
  }
}
