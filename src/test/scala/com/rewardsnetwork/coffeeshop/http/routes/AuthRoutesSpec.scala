package com.rewardsnetwork.coffeeshop.http.routes

import cats.effect.*
import cats.implicits.*
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.data.OptionT

import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.http4s.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s.headers.Authorization
import org.typelevel.ci.CIStringSyntax
import tsec.authentication.{IdentityStore, JWTAuthenticator}
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256

import scala.concurrent.duration.*

import com.rewardsnetwork.coffeeshop.algebras.*
import com.rewardsnetwork.coffeeshop.domain.*
import com.rewardsnetwork.coffeeshop.domain.security.*
import com.rewardsnetwork.coffeeshop.fixtures.*
import com.rewardsnetwork.coffeeshop.domain.auth.*
import com.rewardsnetwork.coffeeshop.domain.user.*
import com.rewardsnetwork.coffeeshop.domain.security.*

class AuthRoutesSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Http4sDsl[IO]
    with UserFixture {
  //-----------------------------
  // setup
  //-----------------------------
  val mockedAuthenticator: Authenticator[IO] = {
    // key for hashing
    val key = HMACSHA256.unsafeGenerateKey
    // identity store to retrieve users
    val idStore: IdentityStore[IO, String, User] = (email: String) => {
      if (email == samEmail) OptionT.pure(Sam)
      else if (email == johnEmail) OptionT.pure(John)
      else OptionT.none[IO, User]
    }
    // jwt authenticator
    JWTAuthenticator.unbacked.inBearerToken(
      1.day,   // expiration of tokens
      None,    // max idle time (optional)
      idStore, // identity store
      key      // hash key
    )
  }

  val mockedAuth: Auth[IO] = new Auth[IO] {
    override def login(email: String, password: String): IO[Option[AuthToken]] =
      if (email == samEmail && password == samPassword)
        mockedAuthenticator.create(samEmail).map(Some(_))
      else None.pure[IO]

    override def signUp(newUserInfo: user.NewUserInfo): IO[Option[user.User]] =
      if (newUserInfo.email == johnEmail)
        IO.pure(Some(John))
      else IO.pure(None)

    override def changePassword(
        email: String,
        newPasswordInfo: NewPasswordInfo
    ): IO[Either[String, Option[user.User]]] = {
      if (email == samEmail) {
        if (newPasswordInfo.oldPassword == samPassword)
          IO.pure(Right(Some(Sam)))
        else
          IO.pure(Left("Invalid Password"))
      } else
        Right(None).pure[IO]
    }

    override def authenticator: Authenticator[IO] = mockedAuthenticator
  }

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val authRoutes: HttpRoutes[IO] = AuthRoutes[IO](mockedAuth).routes

  extension (r: Request[IO])
    def withBearerToken(a: AuthToken): Request[IO] =
      r.putHeaders {
        val jwtString = JWTMac.toEncodedString[IO, HMACSHA256](a.jwt)
        // Authorization: Bearer
        Authorization(Credentials.Token(AuthScheme.Bearer, jwtString))
      }

  //-----------------------------
  // tests
  //-----------------------------

  "AuthRotes" - {
    "should return a 401 - unauthorized if login fails" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/login")
            .withEntity(LoginInfo(samEmail, "wrongpassword"))
        )
      } yield {
        // assertions
        response.status shouldBe Status.Unauthorized
      }
    }

    "should return a 200 - OK + a JWT if login is successful" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/login")
            .withEntity(LoginInfo(samEmail, samPassword))
        )
      } yield {
        // assertions
        response.status shouldBe Status.Ok
        response.headers.get(ci"Authorization") shouldBe defined
      }
    }

    // POST /auth/users { NewUserInfo } => 201 Created or BadRequest
    "should return a 400 - Bad Request if the user to create already exists" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/users")
            .withEntity(NewUserSam)
        )
      } yield {
        // assertions
        response.status shouldBe Status.BadRequest
      }
    }

    "should return a 201 - Created if the user creation succeeds" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/users")
            .withEntity(NewUserJohn)
        )
      } yield {
        // assertions
        response.status shouldBe Status.Created
      }
    }

    "should return a 200 - Ok if logging out with a valid JWT" in {
      for {
        jwtToken <- mockedAuthenticator.create(samEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/logout")
            .withBearerToken(jwtToken)
        )
      } yield {
        // assertions
        response.status shouldBe Status.Ok
      }
    }

    "should return a 401 - Unauthorized if logging out without a JWT" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/logout")
        )
      } yield {
        // assertions
        response.status shouldBe Status.Unauthorized
      }
    }

    "should return a 404 - Not Found if changing password for a user that doesn't exist" in {
      for {
        jwtToken <- mockedAuthenticator.create(johnEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/users/password")
            .withBearerToken(jwtToken)
            .withEntity(NewPasswordInfo(johnPassword, "newpassword"))
        )
      } yield {
        // assertions
        response.status shouldBe Status.NotFound
      }
    }

    "should return a 403 - Forbidden if old password is incorrect" in {
      for {
        jwtToken <- mockedAuthenticator.create(samEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withBearerToken(jwtToken)
            .withEntity(NewPasswordInfo("wrongpassword", "newpassword"))
        )
      } yield {
        // assertions
        response.status shouldBe Status.Forbidden
      }
    }

    "should return a 401 - Unauthorized changing password without a jwt" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withEntity(NewPasswordInfo(samPassword, "newpassword"))
        )
      } yield {
        // assertions
        response.status shouldBe Status.Unauthorized
      }
    }

    "should return a 200 - Ok changing a password for a user with a valid JWT and password" in {
      for {
        jwtToken <- mockedAuthenticator.create(samEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/auth/users/password")
            .withBearerToken(jwtToken)
            .withEntity(NewPasswordInfo(samPassword, "newpassword"))
        )
      } yield {
        // assertions
        response.status shouldBe Status.Ok
      }
    }
  }
}
