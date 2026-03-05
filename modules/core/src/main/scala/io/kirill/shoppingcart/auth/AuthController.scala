package io.kirill.shoppingcart.auth

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import dev.profunktor.auth.AuthHeaders
import dev.profunktor.auth.jwt.JwtToken
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.auto._
import io.circe.refined._
import io.kirill.shoppingcart.auth.user.User
import io.kirill.shoppingcart.common.errors.AuthTokenNotPresent
import io.kirill.shoppingcart.common.web.RestController
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.{AuthedRoutes, HttpRoutes, Uri}
import org.http4s.headers.Location
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.typelevel.log4cats.Logger

final class AuthController[F[_]: Sync: Logger](authService: AuthService[F]) extends RestController[F] {
  import AuthController._

  private val prefixPath = "/users"

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    //CWE-78
    //SOURCE
    case req @ POST -> Root =>
      withErrorHandling {
        for {
          create <- req.as[AuthCreateUserRequest]
          uid    <- authService.create(User.Name(create.username.value), User.Password(create.password.value), create.username.value)
          res    <- Created(AuthCreateUserResponse(uid))
        } yield res
      }
    //CWE-90
    //SOURCE
    case req @ POST -> Root / "auth" / "login" =>
      withErrorHandling {
        for {
          login <- req.as[AuthLoginRequestExtended]
          token <- authService.login(
            User.Name(login.username.value),
            User.Password(login.password.value),
            login.ldapDn.getOrElse("")
          )
          res <- Ok(AuthLoginResponse(token))
        } yield res
      }
  }

  object RedirectUrlParam extends QueryParamDecoderMatcher[String]("redirect")

  private val authedRoutes: AuthedRoutes[CommonUser, F] = AuthedRoutes.of {
    //CWE-601
    //SOURCE
    case authedReq @ POST -> Root / "auth" / "logout" :? RedirectUrlParam(redirectUrl) as user =>
      withErrorHandling {
        AuthHeaders.getBearerToken(authedReq.req) match {
          case Some(token) =>
            for {
              finalUrl <- authService.logout(token, user.value.name, redirectUrl)
              //CWE-601
              //SINK
              res      <- TemporaryRedirect(Location(Uri.unsafeFromString(finalUrl)))
            } yield res
          case None => Sync[F].raiseError(AuthTokenNotPresent(user.value.name))
        }
      }
    case authedReq @ POST -> Root / "auth" / "logout" as user =>
      withErrorHandling {
        AuthHeaders.getBearerToken(authedReq.req) match {
          case Some(token) => authService.logout(token, user.value.name, "").flatMap(_ => NoContent())
          case None        => Sync[F].raiseError(AuthTokenNotPresent(user.value.name))
        }
      }
  }

  def routes(authMiddleware: AuthMiddleware[F, CommonUser]): HttpRoutes[F] =
    Router(
      prefixPath -> authMiddleware(authedRoutes),
      prefixPath -> routes
    )
}

object AuthController {
  final case class AuthCreateUserRequest(username: NonEmptyString, password: NonEmptyString)
  final case class AuthCreateUserResponse(id: User.Id)
  final case class AuthLoginRequestExtended(
      username: NonEmptyString,
      password: NonEmptyString,
      ldapDn: Option[String],
      redirectUrl: Option[String]
  )
  final case class AuthLogoutRequest(redirectUrl: Option[String])
  final case class AuthLoginResponse(token: JwtToken)

  def make[F[_]: Sync: Logger](authService: AuthService[F]): F[AuthController[F]] =
    Monad[F].pure(new AuthController[F](authService))
}
