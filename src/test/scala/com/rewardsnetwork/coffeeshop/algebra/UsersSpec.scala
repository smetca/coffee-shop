package com.rewardsnetwork.coffeeshop.algebra

import cats.effect.*
import cats.effect.testing.scalatest.AsyncIOSpec

import doobie.*
import doobie.implicits.*
import doobie.util.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Inside
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.postgresql.util.PSQLException

import com.rewardsnetwork.coffeeshop.algebras.LiveUsers
import com.rewardsnetwork.coffeeshop.fixtures.UserFixture
import com.rewardsnetwork.coffeeshop.domain.user.*

class UsersSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Inside
    with DoobieSpec
    with UserFixture {
  override val initScript: String = "sql/users.sql"

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  "Users 'algebra'" - {
    "should retrieve a user by email" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          retrieved <- users.find(John.email)
        } yield retrieved

        program.asserting(_ shouldBe Some(John))
      }
    }

    "should return None if the email doesn't exist" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          retrieved <- users.find("idontexist@coffeeshop.com")
        } yield retrieved

        program.asserting(_ shouldBe None)
      }
    }

    "should create a new user" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          userId <- users.create(NewUser)
          userOpt <- sql"SELECT * FROM users WHERE email = ${NewUser.email}"
            .query[User]
            .option
            .transact(xa)
        } yield (userId, userOpt)

        program.asserting {
          case (userId, userOpt) =>
            userId shouldBe NewUser.email
            userOpt shouldBe Some(NewUser)
        }
      }
    }

    "should fail creating a new user if the email already exists" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          userId <- users.create(Sam).attempt
        } yield userId

        program.asserting { outcome =>
          inside(outcome) {
            case Left(e) => e shouldBe a[PSQLException]
            case _       => fail()
          }
        }
      }
    }

    "should return None when updating a user that does not exist" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          userOpt <- users.update(NewUser)
        } yield userOpt

        program.asserting(_ shouldBe None)
      }
    }

    "should update an existing user" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          userOpt <- users.update(UpdatedJohn)
        } yield userOpt

        program.asserting(_ shouldBe Some(UpdatedJohn))
      }
    }

    "should delete a user" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          result <- users.delete(Sam.email) // IO[Either[Throwable, String]]
          userOpt <- sql"SELECT * FROM users WHERE email = ${Sam.email}".query[User]
            .option
            .transact(xa)
        } yield (result, userOpt)

        program.asserting {
          case (result, userOpt) =>
            result shouldBe true
            userOpt shouldBe None
        }
      }
    }

    "should not delete a user that doesn't exist" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          result <- users.delete("idontexist@coffeeshop.com")
        } yield result

        program.asserting(_ shouldBe false)
      }
    }
  }
}
