package com.rewardsnetwork.coffeeshop.http.routes

import io.circe.generic.auto.*
import cats.effect.*
import cats.implicits.*

import tsec.authentication.{SecuredRequestHandler, TSecAuthService, asAuthed}
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.circe.CirceEntityCodec.*
import org.typelevel.log4cats.Logger

import com.rewardsnetwork.coffeeshop.algebras.*
import com.rewardsnetwork.coffeeshop.domain.auth.*
import com.rewardsnetwork.coffeeshop.domain.security.{AuthRoute, AuthToken}
import com.rewardsnetwork.coffeeshop.domain.user.*
import com.rewardsnetwork.coffeeshop.http.validation.syntax.*
import com.rewardsnetwork.coffeeshop.http.responses.*

class AuthRoutes[F[_]: Concurrent: Logger] private (auth: Auth[F]) extends HttpValidationDsl[F] {

  private val authenticator = auth.authenticator
  private val securedHandler: SecuredRequestHandler[F, String, User, AuthToken] =
    SecuredRequestHandler(authenticator)

  // POST /auth/login { LoginInfo } { Authorization: Bearer {token} }
  private val loginRoute: HttpRoutes[F] = HttpRoutes.of[F] { case req @ POST -> Root / "login" =>
    req.validate[LoginInfo] { loginInfo =>
      val tokenOpt = for {
        tokenOpt <- auth.login(loginInfo.email, loginInfo.password)
        _          <- Logger[F].info(s"User logging in: ${loginInfo.email}")
      } yield tokenOpt

      tokenOpt.map {
        case Some(token) => authenticator.embed(Response(Status.Ok), token) // Authorization: Bearer
        case None        => Response(Status.Unauthorized)
      }
    }
  }

  // POST /auth/users { NewUserInfo } => 201 Created
  private val createUserRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "users" =>
      req.validate[NewUserInfo] { newUserInfo =>
        for {
          newUserInfo  <- req.as[NewUserInfo]
          newUserOpt <- auth.signUp(newUserInfo)
          resp <- newUserOpt match {
            case Some(user) => Created(user.email)
            case None       => BadRequest(s"User with email ${newUserInfo.email} already exists")
          }
        } yield resp
      }
  }

  // PUT /auth/users/password { NewPasswordInfo } { Authorization: Bearer {token} }
  private val changePasswordRoute: AuthRoute[F] = {
    case req @ PUT -> Root / "users" / "password" asAuthed user =>
      req.request.validate[NewPasswordInfo] { newPasswordInfo =>
        for {
          newPasswordInfo  <- req.request.as[NewPasswordInfo]
          userOrErrorOpt <- auth.changePassword(user.email, newPasswordInfo)
          resp <- userOrErrorOpt match {
            case Right(Some(_)) => Ok()
            case Right(None)    => NotFound(FailResponse(s"User ${user.email} not found."))
            case Left(_)        => Forbidden()
          }
        } yield resp
      }
  }

  // POST /auth/logout { Authorization: Bearer {token} }
  private val logoutRoute: AuthRoute[F] = { case req @ POST -> Root / "logout" asAuthed _ =>
    val token = req.authenticator
    for {
      _    <- authenticator.discard(token)
      resp <- Ok()
    } yield resp
  }

  val unauthedRoutes = loginRoute <+> createUserRoute
  val authedRoutes = securedHandler.liftService(
    TSecAuthService(changePasswordRoute.orElse(logoutRoute))
  )

  val routes = Router(
    "/auth" -> (unauthedRoutes <+> authedRoutes)
  )
}

object AuthRoutes {
  def apply[F[_]: Concurrent: Logger](auth: Auth[F]) = new AuthRoutes[F](auth)
}
