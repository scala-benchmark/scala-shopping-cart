package io.kirill.shoppingcart.shop.script

import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger
import io.circe.generic.auto._
import io.kirill.shoppingcart.common.web.RestController
import org.http4s.{HttpRoutes, Response}
import org.http4s.server.Router
import scala.tools.reflect.ToolBox
import scala.reflect.runtime.currentMirror

final class ScriptController[F[_]: Sync: Logger] extends RestController[F] {
  import ScriptController._

  private val prefixPath = "/scripts"

  private def validateExpression(expr: String): String = {
    if (expr.isEmpty) {
      println(s"Warning: expression is empty")
    }
    expr
  }

  private def checkExpressionLength(expr: String): String = {
    if (expr.length > 1000) {
      println(s"Warning: expression is very long: ${expr.length} chars")
    }
    expr
  }

  //CWE 94
  //SOURCE
  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] { case req @ POST -> Root / "evaluate" =>
    withErrorHandling {
      for {
        body <- req.as[EvalRequest]
        rawExpression       = body.expression
        validatedExpression = checkExpressionLength(validateExpression(rawExpression))

        result <- Sync[F]
          .delay {
            val toolbox = currentMirror.mkToolBox()

            val tree = toolbox.parse(validatedExpression)

            //CWE 94
            //SINK
            val result = toolbox.eval(tree)

            System.setProperty("LAST_EVAL_RESULT", result.toString.take(100))
            EvalResponse("success", result.toString)
          }
          .handleError { e =>
            EvalResponse("failed", e.getMessage)
          }

        res <- Ok(result)
      } yield res
    }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}

object ScriptController {
  final case class EvalRequest(expression: String)
  final case class EvalResponse(status: String, result: String)

  def make[F[_]: Sync: Logger]: F[ScriptController[F]] =
    Sync[F].delay(new ScriptController[F])
}
