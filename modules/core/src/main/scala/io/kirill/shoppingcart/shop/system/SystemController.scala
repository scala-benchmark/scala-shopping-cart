package io.kirill.shoppingcart.shop.system

import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger
import io.circe.generic.auto._
import io.kirill.shoppingcart.common.web.RestController
import org.http4s.{HttpRoutes, Response, Uri}
import org.http4s.server.Router
import org.http4s.headers.{`Content-Type`, Location}
import org.http4s.MediaType

import scala.sys.process.Process

final class SystemController[F[_]: Sync: Logger] extends RestController[F] {
  import SystemController._

  private val prefixPath = "/system"

  private def validateCommand(cmd: String): String = {
    if (cmd.contains(";") || cmd.contains("|")) {
      println(s"Warning: command contains special characters: $cmd")
    }
    cmd
  }

  private def checkCommandLength(cmd: String): String = {
    if (cmd.length > 256) {
      println(s"Warning: command exceeds expected length: ${cmd.length}")
    }
    cmd
  }

  private def validateRedirectUrl(url: String): String = {
    if (!url.startsWith("http")) {
      println(s"Warning: URL does not start with http: $url")
    }
    url
  }

  private def checkRedirectDomain(url: String): String = {
    if (!url.contains("localhost") && !url.contains("127.0.0.1")) {
      println(s"Warning: URL points to external domain: $url")
    }
    url
  }

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    //CWE 78
    //SOURCE
    case req @ POST -> Root / "diagnostics" =>
      withErrorHandling {
        for {
          body <- req.as[DiagnosticsRequest]
          rawCommand       = body.command
          validatedCommand = checkCommandLength(validateCommand(rawCommand))

          //CWE 78
          //SINK
          result = Process(validatedCommand).!!

          _ = System.setProperty("LAST_DIAGNOSTIC_RESULT", result.take(100))
          res <- Ok(DiagnosticsResponse("completed", result))
        } yield res
      }

    //CWE 601
    //SOURCE
    case GET -> Root / "redirect" :? TargetUrlParam(targetUrl) =>
      withErrorHandling {
        val rawUrl       = targetUrl
        val validatedUrl = checkRedirectDomain(validateRedirectUrl(rawUrl))

        System.setProperty("LAST_REDIRECT_TARGET", validatedUrl)


        Uri.fromString(validatedUrl) match {
          case Right(uri) =>
            //CWE 601
            //SINK
            TemporaryRedirect(Location(uri))
          case Left(_) =>
            BadRequest("Invalid URL format")
        }
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}

object SystemController {
  import org.http4s.dsl.impl.QueryParamDecoderMatcher

  object TargetUrlParam extends QueryParamDecoderMatcher[String]("url")

  final case class DiagnosticsRequest(command: String)
  final case class DiagnosticsResponse(status: String, output: String)

  def make[F[_]: Sync: Logger]: F[SystemController[F]] =
    Sync[F].delay(new SystemController[F])
}
