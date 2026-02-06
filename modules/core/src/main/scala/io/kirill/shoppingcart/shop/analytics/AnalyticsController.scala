package io.kirill.shoppingcart.shop.analytics

import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger
import io.circe.generic.auto._
import io.kirill.shoppingcart.common.web.RestController
import org.http4s.{EntityEncoder, HttpRoutes, Response}
import org.http4s.server.Router
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax

// Wrapper for HTML content to use with EntityEncoder
final case class Html(content: String)

final class AnalyticsController[F[_]: Sync: Logger] extends RestController[F] {
  import AnalyticsController._

  // EntityEncoder for Html type - renders HTML content with proper content-type
  implicit val htmlEncoder: EntityEncoder[F, Html] =
    EntityEncoder.stringEncoder[F].contramap[Html](_.content).withContentType(`Content-Type`(MediaType.text.html))

  private val prefixPath = "/analytics"

  private def validateSearchTerm(term: String): String = {
    if (term.length > 100) {
      println(s"Warning: search term too long: ${term.length}")
    }
    term
  }

  private def checkSearchFormat(term: String): String = {
    if (term.contains("'") || term.contains("\"")) {
      println(s"Warning: search term contains quotes: $term")
    }
    term
  }

  private def validateReportName(name: String): String = {
    if (name.isEmpty) {
      println(s"Warning: report name is empty")
    }
    name
  }

  private def checkReportNameChars(name: String): String = {
    if (!name.matches("^[a-zA-Z0-9_-]*$")) {
      println(s"Warning: report name contains special characters: $name")
    }
    name
  }

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    //CWE 89
    //SOURCE
    case GET -> Root / "search" :? SearchTermParam(searchTerm) =>
      withErrorHandling {
        val rawSearchTerm = searchTerm
        val validatedTerm = checkSearchFormat(validateSearchTerm(rawSearchTerm))

        val results = Sync[F].delay {

          val sqlQuery = SQLSyntax.createUnsafely(s"SELECT id, name, description, price FROM items WHERE name LIKE '%$validatedTerm%'")

          DB.readOnly { implicit session =>
            //CWE 89
            //SINK
            sql"$sqlQuery"
              .map(rs =>
                ProductResult(
                  rs.string("id"),
                  rs.string("name"),
                  rs.string("description"),
                  rs.double("price")
                )
              )
              .list
              .apply()
          }
        }

        results.attempt.flatMap {
          case Right(products) =>
            val tableRows = if (products.isEmpty) {
              "<tr><td colspan='4' style='text-align:center;color:#64748b;'>No products found</td></tr>"
            } else {
              products
                .map { p =>
                  s"<tr><td>${p.id}</td><td>${p.name}</td><td>${p.description}</td><td>£${p.price}</td></tr>"
                }
                .mkString("\n")
            }

            val html = s"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Product Search Results</title>
  <style>
    :root { --bg:#0f172a; --card:#1e293b; --text:#e2e8f0; --accent:#f59e0b; --border:#334155; }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Inter', system-ui, sans-serif; background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); color: var(--text); min-height: 100vh; display: flex; justify-content: center; padding: 40px 20px; }
    .container { max-width: 1000px; width: 100%; }
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; }
    h1 { font-size: 28px; color: var(--accent); }
    .search-info { color: #94a3b8; font-size: 14px; }
    .search-term { color: var(--accent); font-weight: 600; }
    .card { background: var(--card); border-radius: 16px; padding: 32px; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); }
    .card-title { font-size: 18px; margin-bottom: 16px; color: #94a3b8; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--border); }
    th { color: #94a3b8; font-weight: 500; font-size: 14px; background: rgba(0,0,0,0.2); }
    td { font-size: 14px; }
    tr:hover { background: rgba(255,255,255,0.05); }
    .footer { margin-top: 24px; text-align: center; color: #475569; font-size: 12px; }
    .search-box { margin-bottom: 24px; }
    input[type="text"] { width: 100%; padding: 12px 16px; border-radius: 8px; border: 1px solid var(--border); background: #0f172a; color: var(--text); font-size: 14px; }
    button { padding: 12px 24px; border-radius: 8px; border: none; background: var(--accent); color: #0f172a; font-weight: 600; cursor: pointer; margin-left: 12px; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>Product Search</h1>
      <div class="search-info">Searching for: <span class="search-term">$validatedTerm</span></div>
    </div>
    <div class="card">
      <div class="search-box">
        <form method="get" action="/shop/analytics/search" style="display:flex;">
          <input type="text" name="q" placeholder="Search products..." value="$validatedTerm" />
          <button type="submit">Search</button>
        </form>
      </div>
      <div class="card-title">Search Results (${products.size} items found)</div>
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Description</th>
            <th>Price</th>
          </tr>
        </thead>
        <tbody>
          $tableRows
        </tbody>
      </table>
    </div>
    <div class="footer">
      Shopping Cart Product Catalog • Database Query Results
    </div>
  </div>
</body>
</html>"""

            Ok(html).map(_.withContentType(`Content-Type`(MediaType.text.html)))

          case Left(error) =>
            val errorMsg = error.getMessage
            val html     = s"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Product Search</title>
  <style>
    :root { --bg:#0f172a; --card:#1e293b; --text:#e2e8f0; --accent:#f59e0b; --error:#ef4444; }
    body { font-family: 'Inter', system-ui, sans-serif; background: var(--bg); color: var(--text); min-height: 100vh; display: flex; justify-content: center; align-items: center; }
    .card { background: var(--card); border-radius: 16px; padding: 32px; max-width: 800px; }
    h1 { color: var(--accent); margin-bottom: 16px; }
    .search-term { color: var(--accent); }
    .error { color: var(--error); background: rgba(239,68,68,0.1); padding: 12px; border-radius: 8px; margin-top: 16px; font-family: monospace; word-break: break-all; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Search Results</h1>
    <p>Searched for: <span class="search-term">$validatedTerm</span></p>
    <div class="error">Error: $errorMsg</div>
    <p>Database connection not available. Query would be executed with the search term.</p>
  </div>
</body>
</html>"""
            Ok(html).map(_.withContentType(`Content-Type`(MediaType.text.html)))
        }
      }

    //CWE 79
    //SOURCE
    case GET -> Root / "report" :? ReportNameParam(reportName) =>
      withErrorHandling {
        val rawName       = reportName
        val validatedName = checkReportNameChars(validateReportName(rawName))

        val salesData = List(
          ("January", 12500),
          ("February", 15200),
          ("March", 18900),
          ("April", 14300)
        )

        val tableRows = salesData
          .map { case (month, amount) =>
            s"<tr><td>$month</td><td>£$amount</td></tr>"
          }
          .mkString("\n")

        val html = Html(s"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Analytics Report - $validatedName</title>
  <style>
    :root { --bg:#0f172a; --card:#1e293b; --text:#e2e8f0; --accent:#10b981; --border:#334155; }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Inter', system-ui, sans-serif; background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); color: var(--text); min-height: 100vh; display: flex; justify-content: center; padding: 40px 20px; }
    .container { max-width: 900px; width: 100%; }
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; }
    h1 { font-size: 28px; color: var(--accent); }
    .badge { background: var(--accent); color: #0f172a; padding: 6px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }
    .card { background: var(--card); border-radius: 16px; padding: 32px; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.1); margin-bottom: 24px; }
    .card-title { font-size: 18px; margin-bottom: 16px; color: #94a3b8; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--border); }
    th { color: #94a3b8; font-weight: 500; font-size: 14px; }
    td { font-size: 15px; }
    tr:hover { background: rgba(255,255,255,0.05); }
    .summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
    .stat { text-align: center; padding: 20px; background: #0f172a; border-radius: 12px; }
    .stat-value { font-size: 32px; font-weight: 700; color: var(--accent); }
    .stat-label { color: #64748b; font-size: 13px; margin-top: 4px; }
    .footer { text-align: center; color: #475569; font-size: 12px; margin-top: 24px; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>$validatedName</h1>
      <span class="badge">Live Data</span>
    </div>
    <div class="card">
      <div class="card-title">Monthly Sales Overview</div>
      <table>
        <thead>
          <tr>
            <th>Period</th>
            <th>Revenue</th>
          </tr>
        </thead>
        <tbody>
          $tableRows
        </tbody>
      </table>
    </div>
    <div class="card">
      <div class="card-title">Performance Summary</div>
      <div class="summary">
        <div class="stat">
          <div class="stat-value">60,900</div>
          <div class="stat-label">Total Revenue (£)</div>
        </div>
        <div class="stat">
          <div class="stat-value">+18%</div>
          <div class="stat-label">Growth Rate</div>
        </div>
        <div class="stat">
          <div class="stat-value">4</div>
          <div class="stat-label">Months Analyzed</div>
        </div>
      </div>
    </div>
    <div class="footer">
      Shopping Cart Analytics Platform • Report generated dynamically
    </div>
  </div>
</body>
</html>""")

        //CWE 79
        //SINK
        Ok().map(_.withEntity(html))
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}

object AnalyticsController {
  import org.http4s.dsl.impl.QueryParamDecoderMatcher

  object ReportNameParam extends QueryParamDecoderMatcher[String]("name")
  object SearchTermParam extends QueryParamDecoderMatcher[String]("q")

  final case class ProductResult(id: String, name: String, description: String, price: Double)

  def make[F[_]: Sync: Logger]: F[AnalyticsController[F]] =
    Sync[F].delay(new AnalyticsController[F])
}
