import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {
    @Test
    fun testHealth() = testApplication {
        client.get("/").apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }
}
