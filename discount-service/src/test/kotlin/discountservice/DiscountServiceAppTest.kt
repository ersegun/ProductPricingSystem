package discountservice

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscountServiceAppTest {

    companion object {
        private const val TEST_TOKEN = "secret-dev-token-please-change"
    }

    @Test
    fun testApplyDiscountSuccess() = testApplication {
        application { module() }

        val response = client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody(
                """{"productId":"prod-1","discountId":"disc-10","percent":15.0}"""
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"applied\":true"))
        //assertTrue(body.contains("\"alreadyApplied\":false"))
    }

    @Test
    fun testApplyDiscountDuplicate() = testApplication {
        application { module() }

        val request = """{"productId":"prod-2","discountId":"disc-20","percent":10.0}"""

        // Apply first time
        client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        // Apply second time - should be deduplicated
        val response = client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"applied\":false"))
        assertTrue(body.contains("\"alreadyApplied\":true"))
    }

    @Test
    fun testApplyDiscountWithoutAuth() = testApplication {
        application { module() }

        val response = client.put("/discounts/apply") {
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"prod-3","discountId":"disc-30","percent":5.0}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testApplyDiscountWithInvalidToken() = testApplication {
        application { module() }

        val response = client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"prod-4","discountId":"disc-40","percent":20.0}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testGetDiscountsForProductWithNoDiscounts() = testApplication {
        application { module() }

        val response = client.get("/discounts/prod-999") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertEquals("[]", body)
    }

    @Test
    fun testGetDiscountsForProductWithDiscounts() = testApplication {
        application { module() }

        val productId = "prod-5"

        // Apply first discount
        client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"$productId","discountId":"disc-50","percent":10.0}""")
        }

        // Apply second discount
        client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"$productId","discountId":"disc-51","percent":15.0}""")
        }

        // Now get the discounts
        val response = client.get("/discounts/$productId") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("disc-50"))
        assertTrue(body.contains("disc-51"))
        assertTrue(body.contains("10.0"))
        assertTrue(body.contains("15.0"))
    }

    @Test
    fun testGetDiscountsWithoutAuth() = testApplication {
        application { module() }

        val response = client.get("/discounts/prod-6")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testMultipleProductsIndependentDiscounts() = testApplication {
        application { module() }

        // Apply discount to product 1
        client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"prod-7","discountId":"disc-70","percent":10.0}""")
        }

        // Apply discount to product 2
        client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"prod-8","discountId":"disc-80","percent":20.0}""")
        }

        // Verify product 1 has only its discount
        val response1 = client.get("/discounts/prod-7") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }
        val body1 = response1.bodyAsText()
        assertTrue(body1.contains("disc-70"))
        assertFalse(body1.contains("disc-80"))

        // Verify product 2 has only its discount
        val response2 = client.get("/discounts/prod-8") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }
        val body2 = response2.bodyAsText()
        assertTrue(body2.contains("disc-80"))
        assertFalse(body2.contains("disc-70"))
    }

    @Test
    fun testApplyMultipleDiscountsToSameProduct() = testApplication {
        application { module() }

        val productId = "prod-9"

        // Apply multiple different discounts
        client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"$productId","discountId":"disc-90","percent":5.0}""")
        }

        client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"$productId","discountId":"disc-91","percent":10.0}""")
        }

        client.put("/discounts/apply") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("""{"productId":"$productId","discountId":"disc-92","percent":15.0}""")
        }

        // Verify all discounts were applied
        val response = client.get("/discounts/$productId") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }

        val body = response.bodyAsText()
        assertTrue(body.contains("disc-90"))
        assertTrue(body.contains("disc-91"))
        assertTrue(body.contains("disc-92"))
    }
}