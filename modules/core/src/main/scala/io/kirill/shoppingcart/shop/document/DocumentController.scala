package io.kirill.shoppingcart.shop.document

import cats.effect.{Sync, Timer}
import cats.implicits._
import org.typelevel.log4cats.Logger
import io.circe.generic.auto._
import io.kirill.shoppingcart.common.web.RestController
import org.http4s.{HttpRoutes, Response}
import org.http4s.server.Router

import scala.concurrent.duration._

final class DocumentController[F[_]: Sync: Timer: Logger] extends RestController[F] {
  import DocumentController._

  private val prefixPath = "/documents"

  private def validateDuration(seconds: Long): Long = {
    if (seconds < 0 || seconds > 3600) {
      println(s"Warning: duration out of expected range: $seconds")
    }
    seconds
  }

  private def checkDurationLimit(seconds: Long): Long = {
    if (seconds > 300) {
      println(s"Warning: duration exceeds soft limit: $seconds")
    }
    seconds
  }

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    //CWE 400
    //SOURCE
    case GET -> Root / "process" :? DelayParam(delay) =>
      withErrorHandling {
        val rawDelay       = delay
        val validatedDelay = checkDurationLimit(validateDuration(rawDelay))

        //CWE 400
        //SINK
        Timer[F].sleep(validatedDelay.seconds) *> {
          System.setProperty("LAST_PROCESS_DELAY", validatedDelay.toString)

          Ok(DocumentProcessResponse("Processing completed", validatedDelay))
        }
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}

object DocumentController {
  import org.http4s.dsl.impl.QueryParamDecoderMatcher
  import cats.effect.{Sync, Timer}

  object DelayParam extends QueryParamDecoderMatcher[Long]("delay")

  final case class DocumentProcessResponse(status: String, delayApplied: Long)

  def make[F[_]: Sync: Timer: Logger]: F[DocumentController[F]] =
    Sync[F].delay(new DocumentController[F])
}
