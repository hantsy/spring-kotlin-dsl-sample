package com.example.demo


import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(classes = [DemoApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [TestConfigInitializer::class])
class IntegrationTests {

    private lateinit var client: WebTestClient

    @LocalServerPort
    private var port: Int = 8080

    @BeforeAll
    fun setup() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `get all posts`() {
        client.get()
            .uri("/posts")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Post::class.java).hasSize(2)
    }

    @Test
    fun `get none existing post should return 404`() {
        client.get()
            .uri("/posts/notexisted")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `create a post without auth should fail with 401`() {
        client.post()
            .uri("/posts")
            .bodyValue(Post(content = "test post"))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `create a post withs auth should ok`() {
        client.post()
            .uri("/posts")
            .bodyValue(Post(content = "test post"))
            .headers {
                it.setBasicAuth("user", "password")
                it.contentType = MediaType.APPLICATION_JSON
            }
            .exchange()
            .expectStatus().isCreated
            .expectHeader().exists("Location")
    }
}
