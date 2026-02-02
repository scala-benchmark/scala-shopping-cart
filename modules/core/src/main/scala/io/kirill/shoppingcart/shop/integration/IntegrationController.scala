package io.kirill.shoppingcart.shop.integration

import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger
import io.circe.generic.auto._
import io.kirill.shoppingcart.common.web.RestController
import org.http4s.{HttpRoutes, Response}
import org.http4s.server.Router
import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import com.unboundid.ldap.sdk._

final class IntegrationController[F[_]: Sync: Logger] extends RestController[F] {
  import IntegrationController._

  private val prefixPath = "/integration"

  private def validateLdapFilter(filter: String): String = {
    if (filter.contains("*") && filter.length < 3) {
      println(s"Warning: LDAP filter too short with wildcard: $filter")
    }
    filter
  }

  private def checkLdapSpecialChars(filter: String): String = {
    if (filter.contains(")(") || filter.contains(")(|")) {
      println(s"Warning: LDAP filter contains potentially dangerous chars: $filter")
    }
    filter
  }

  private def validateClassName(className: String): String = {
    if (className.isEmpty) {
      println(s"Warning: class name is empty")
    }
    className
  }

  private def checkClassNameFormat(className: String): String = {
    if (!className.contains(".")) {
      println(s"Warning: class name does not appear to be fully qualified: $className")
    }
    className
  }

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    //CWE 90
    //SOURCE
    case req @ POST -> Root / "directory" / "search" =>
      withErrorHandling {
        for {
          body <- req.as[LdapSearchRequest]
          rawFilter       = body.filter
          validatedFilter = checkLdapSpecialChars(validateLdapFilter(rawFilter))

          result <- Sync[F].delay {
            val ldapHost = System.getProperty("LDAP_HOST", "localhost")
            val ldapPort = System.getProperty("LDAP_PORT", "389").toInt

            val connection = new LDAPConnection(ldapHost, ldapPort)
            try {
              //CWE 90
              //SINK
              val searchResult = connection.search(body.baseDn,SearchScope.SUB,validatedFilter)

              val entries = (0 until searchResult.getEntryCount).map { i =>
                val entry = searchResult.getSearchEntries.get(i)
                LdapEntry(entry.getDN, entry.getAttributeValue("cn"))
              }.toList

              LdapSearchResponse("success", entries)
            } finally connection.close()
          }

          _ = System.setProperty("LAST_LDAP_FILTER", validatedFilter)
          res <- Ok(result)
        } yield res
      }

    //CWE 502
    //SOURCE
    case req @ POST -> Root / "config" / "import" =>
      withErrorHandling {
        for {
          body <- req.as[ImportRequest]
          rawClassName       = body.targetClass
          validatedClassName = checkClassNameFormat(validateClassName(rawClassName))

          result <- Sync[F].delay {
            val system = ActorSystem("ImportSystem")
            try {
              val serialization = SerializationExtension(system)
              val targetClass  = Class.forName(validatedClassName)
              //CWE 502
              //SINK
              val deserialized = serialization.deserialize(body.data.getBytes("ISO-8859-1"), targetClass)

              deserialized match {
                case scala.util.Success(obj) =>
                  System.setProperty("IMPORTED_CONFIG_TYPE", obj.getClass.getName)
                  ImportResponse("success", s"Imported: ${obj.getClass.getSimpleName}")
                case scala.util.Failure(e) =>
                  ImportResponse("failed", e.getMessage)
              }
            } finally system.terminate()
          }

          res <- Ok(result)
        } yield res
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}

object IntegrationController {
  final case class LdapSearchRequest(baseDn: String, filter: String)
  final case class LdapEntry(dn: String, commonName: String)
  final case class LdapSearchResponse(status: String, entries: List[LdapEntry])

  final case class ImportRequest(targetClass: String, data: String)
  final case class ImportResponse(status: String, message: String)

  def make[F[_]: Sync: Logger]: F[IntegrationController[F]] =
    Sync[F].delay(new IntegrationController[F])
}
