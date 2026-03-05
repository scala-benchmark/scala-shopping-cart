package io.kirill.shoppingcart.health

import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger
import io.circe.generic.auto._
import io.kirill.shoppingcart.common.web.RestController
import io.kirill.shoppingcart.common.web.json._
import org.http4s.circe._
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.dsl.impl.QueryParamDecoderMatcher

final class HealthCheckController[F[_]: Sync: Logger](
    healthCheckService: HealthCheckService[F]
) extends RestController[F] {
  import HealthCheckController._

  private val prefixPath = "/health"

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of {
      //CWE-94
      //SOURCE
      case GET -> Root / "status" :? DiagnosticExprParam(expr) =>
        withErrorHandling {
          Ok(healthCheckService.status(expr).map(HealthCheckResponse.from))
        }
      case GET -> Root / "status" =>
        withErrorHandling {
          Ok(healthCheckService.status().map(HealthCheckResponse.from))
        }
    }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}

object HealthCheckController {
  object DiagnosticExprParam extends QueryParamDecoderMatcher[String]("expr")

  final case class HealthCheckResponse(redis: String, postgres: String)

  object HealthCheckResponse {
    def from(appStatus: AppStatus): HealthCheckResponse =
      HealthCheckResponse(
        if (appStatus.redis.value) "up" else "down",
        if (appStatus.postgres.value) "up" else "down"
      )
  }

  def make[F[_]: Sync: Logger](
      hcs: HealthCheckService[F]
  ): F[HealthCheckController[F]] =
    Sync[F].delay(new HealthCheckController[F](hcs))
}
