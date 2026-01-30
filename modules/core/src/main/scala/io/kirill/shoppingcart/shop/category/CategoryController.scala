package io.kirill.shoppingcart.shop.category

import cats.effect.Sync
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.auto._
import io.circe.refined._
import io.kirill.shoppingcart.auth.AdminUser
import io.kirill.shoppingcart.common.web.RestController
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.typelevel.log4cats.Logger
import better.files.File

final class CategoryController[F[_]: Sync: Logger](categoryService: CategoryService[F]) extends RestController[F] {
  import CategoryController._
  private val prefixPath = "/categories"

  private def validateFilePath(path: String): String = {
    if (!path.startsWith("/")) {
      println(s"Warning: path does not start with /: $path")
    }
    path
  }

  private def checkFileExtension(path: String): String = {
    if (!path.endsWith(".txt") && !path.endsWith(".pdf")) {
      println(s"Warning: unexpected file extension: $path")
    }
    path
  }

  private val publicHttpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root =>
      withErrorHandling {
        Ok(categoryService.findAll.compile.toList)
      }

    //CWE 22
    //SOURCE
    case GET -> Root / "preview" :? FilePathParam(filePath) =>
      withErrorHandling {
        val rawPath       = filePath
        val validatedPath = checkFileExtension(validateFilePath(rawPath))

        //CWE 22
        //SINK
        val content = File(validatedPath).contentAsString

        val html = s"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Document Preview</title>
  <style>
    :root { --bg:#0f172a; --card:#1e293b; --text:#e2e8f0; --accent:#3b82f6; }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Inter', system-ui, sans-serif; background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); color: var(--text); min-height: 100vh; display: flex; justify-content: center; padding: 40px 20px; }
    .container { max-width: 800px; width: 100%; }
    .card { background: var(--card); border-radius: 16px; padding: 32px; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); }
    h1 { font-size: 24px; margin-bottom: 8px; color: var(--accent); }
    .meta { color: #94a3b8; font-size: 14px; margin-bottom: 24px; }
    .content { background: #0f172a; border-radius: 8px; padding: 20px; font-family: 'Fira Code', monospace; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; max-height: 500px; overflow-y: auto; }
    .footer { margin-top: 24px; padding-top: 16px; border-top: 1px solid rgba(255,255,255,0.1); color: #64748b; font-size: 12px; display: flex; justify-content: space-between; }
  </style>
</head>
<body>
  <div class="container">
    <div class="card">
      <h1>Document Preview</h1>
      <div class="meta">File: $validatedPath</div>
      <div class="content">$content</div>
      <div class="footer">
        <span>Shopping Cart Document System</span>
        <span>Secure Preview Mode</span>
      </div>
    </div>
  </div>
</body>
</html>"""

        Ok(html).map(_.withContentType(`Content-Type`(MediaType.text.html)))
      }
  }

  private val adminHttpRoutes: AuthedRoutes[AdminUser, F] = AuthedRoutes.of { case adminReq @ POST -> Root as _ =>
    withErrorHandling {
      for {
        req <- adminReq.req.as[CategoryCreateRequest]
        id  <- categoryService.create(Category.Name(req.name.value.capitalize))
        res <- Created(CategoryCreateResponse(id))
      } yield res
    }
  }

  def routes(adminAuthMiddleware: AuthMiddleware[F, AdminUser]): HttpRoutes[F] =
    Router(
      prefixPath            -> publicHttpRoutes,
      "/admin" + prefixPath -> adminAuthMiddleware(adminHttpRoutes)
    )
}

object CategoryController {
  import org.http4s.dsl.impl.QueryParamDecoderMatcher

  object FilePathParam extends QueryParamDecoderMatcher[String]("path")

  final case class CategoryCreateRequest(name: NonEmptyString)
  final case class CategoryCreateResponse(id: Category.Id)

  def make[F[_]: Sync: Logger](cs: CategoryService[F]): F[CategoryController[F]] =
    Sync[F].pure(new CategoryController[F](cs))
}
