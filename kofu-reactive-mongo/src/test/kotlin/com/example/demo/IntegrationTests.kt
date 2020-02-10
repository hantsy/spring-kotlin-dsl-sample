package com.example.demo


import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class IntegrationTests {

    private val client = WebTestClient.bindToServer().baseUrl("http://localhost:8181").build()

    private lateinit var context: ConfigurableApplicationContext

    @BeforeAll
    fun beforeAll() {
        context = app.run(profiles = "test")
    }

    @AfterAll
    fun afterAll() {
        context.close()
    }

    @Test
    fun `get all posts`() {
        client.get()
                .uri("/posts")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.[0].title").isEqualTo("post one")
                .jsonPath("$.[1].title").isEqualTo("post two")

    }

    @Test
    fun `get none existing post should return 404`() {
        client.get()
                .uri("/posts/notexisted")
                .exchange()
                .expectStatus().isNotFound
    }

    @Test
    @Disabled //TODO: add security config later when Spring Fu support it officially
    fun `create a post without auth should fail with 401`() {
        client.post()
                .uri("/posts")
                .bodyValue(Post(content = "test post"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
    }

    @Test
    fun `create a post should return 201`() {
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
