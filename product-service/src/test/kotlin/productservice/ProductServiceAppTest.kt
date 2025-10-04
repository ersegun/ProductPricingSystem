package productservice

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ProductServiceAppTest {

    companion object {
        private const val TEST_TOKEN = "secret-dev-token-please-change"
    }

    @Test
    fun testGetProductsWithoutCountryParam() = testApplication {
        application { module() }

        val response = client.get("/products") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("country is required"))
    }

    @Test
    fun testGetProductsWithoutAuth() = testApplication {
        application { module() }

        val response = client.get("/products?country=SE")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testGetProductsWithInvalidToken() = testApplication {
        application { module() }

        val response = client.get("/products?country=SE") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testPostProductDiscountMismatchedId() = testApplication {
        application { module() }

        val response = client.post("/products/prod-1/discount") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"prod-2","discountId":"disc-1","percent":10.0}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Path id must match productId"))
    }

    @Test
    fun testPostAdminIngest() = testApplication {
        application { module() }

        val response = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"products","chunkSize":50,"retries":3,"dryRun":true,"failFast":false}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("ingestionId"))
        assertTrue(body.contains("\"status\":\"started\""))
    }

    @Test
    fun testPostAdminIngestWithoutAuth() = testApplication {
        application { module() }

        val response = client.post("/admin/ingest") {
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"all"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testGetAdminIngestStatusWithoutAuth() = testApplication {
        application { module() }

        val response = client.get("/admin/ingest/ing-123/status")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testGetAdminIngestStatusNotFound() = testApplication {
        application { module() }

        val response = client.get("/admin/ingest/non-existent-id/status") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun testAdminIngestThenCheckStatus() = testApplication {
        application { module() }

        // Start ingestion
        val startResponse = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"products","dryRun":true}""")
        }

        assertEquals(HttpStatusCode.OK, startResponse.status)
        val startBody = startResponse.bodyAsText()

        // Extract ingestion ID from response
        val ingestionIdMatch = Regex(""""ingestionId":"([^"]+)"""").find(startBody)
        assertNotNull(ingestionIdMatch)
        val ingestionId = ingestionIdMatch.groupValues[1]

        // Check status immediately
        val statusResponse = client.get("/admin/ingest/$ingestionId/status") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }

        assertEquals(HttpStatusCode.OK, statusResponse.status)
        val statusBody = statusResponse.bodyAsText()
        assertTrue(statusBody.contains("ingestionId"))
        assertTrue(statusBody.contains("status"))
        assertTrue(statusBody.contains("filesDiscovered"))
    }

    @Test
    fun testAdminIngestProductsOnly() = testApplication {
        application { module() }

        val response = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"products","dryRun":true}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testAdminIngestDiscountsOnly() = testApplication {
        application { module() }

        val response = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"discounts","dryRun":true}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testAdminIngestAllMode() = testApplication {
        application { module() }

        val response = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"all","dryRun":true}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testAdminIngestDefaultMode() = testApplication {
        application { module() }

        val response = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"dryRun":true}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testAdminIngestWithCustomChunkSize() = testApplication {
        application { module() }

        val response = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"products","chunkSize":10,"dryRun":true}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testAdminIngestWithCustomRetries() = testApplication {
        application { module() }

        val response = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"products","retries":5,"dryRun":true}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testAdminIngestWithFailFast() = testApplication {
        application { module() }

        val response = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"products","failFast":true,"dryRun":true}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testMultipleIngestionsGetDifferentIds() = testApplication {
        application { module() }

        val response1 = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"dryRun":true}""")
        }

        Thread.sleep(10) // Ensure different timestamps

        val response2 = client.post("/admin/ingest") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"dryRun":true}""")
        }

        val body1 = response1.bodyAsText()
        val body2 = response2.bodyAsText()

        val id1 = Regex(""""ingestionId":"([^"]+)"""").find(body1)?.groupValues?.get(1)
        val id2 = Regex(""""ingestionId":"([^"]+)"""").find(body2)?.groupValues?.get(1)

        assertNotNull(id1)
        assertNotNull(id2)
        assertNotEquals(id1, id2)
    }
}