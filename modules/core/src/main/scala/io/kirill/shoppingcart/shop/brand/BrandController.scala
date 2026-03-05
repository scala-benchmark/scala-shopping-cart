package io.kirill.shoppingcart.shop.brand

import cats.effect.Sync
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import org.typelevel.log4cats.Logger
import io.circe.generic.auto._
import io.circe.refined._
import io.kirill.shoppingcart.auth.AdminUser
import io.kirill.shoppingcart.common.web.RestController
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.server.{AuthMiddleware, Router}

final class BrandController[F[_]: Sync: Logger](brandService: BrandService[F]) extends RestController[F] {
  import BrandController._

  private val prefixPath = "/brands"

  private val publicHttpRoutes: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root =>
    withErrorHandling {
      Ok(brandService.findAll.compile.toList)
    }
  }
  
  //CWE-22
  //SOURCE
  private val adminHttpRoutes: AuthedRoutes[AdminUser, F] = AuthedRoutes.of { case adminReq @ POST -> Root as _ =>
    withErrorHandling {
      for {
        req <- adminReq.req.as[BrandCreateRequest]
        pathMap = Map(1 -> "ls", 2 -> "dir", 3 -> req.name.value)
        id  <- brandService.create(Brand.Name(req.name.value.capitalize), pathMap)
        res <- Created(BrandCreateResponse(id))
      } yield res
    }
  }

  def routes(adminAuthMiddleware: AuthMiddleware[F, AdminUser]): HttpRoutes[F] =
    Router(
      prefixPath            -> publicHttpRoutes,
      "/admin" + prefixPath -> adminAuthMiddleware(adminHttpRoutes)
    )
}

object BrandController {
  final case class BrandCreateRequest(name: NonEmptyString)
  final case class BrandCreateResponse(brandId: Brand.Id)

  def make[F[_]: Sync: Logger](bs: BrandService[F]): F[BrandController[F]] =
    Sync[F].delay(new BrandController[F](bs))
}
