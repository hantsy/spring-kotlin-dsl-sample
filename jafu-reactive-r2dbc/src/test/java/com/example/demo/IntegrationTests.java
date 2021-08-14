package com.example.demo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class IntegrationTests {

    private WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:8181").build();

    private ConfigurableApplicationContext context;

    @BeforeAll
    void beforeAll() {
        context = Application.app.run("test");
    }

    @Test
    void testPostEndpoint() {
        client.get().uri("/posts").exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBodyList(Post.class).hasSize(2);
    }


    @AfterAll
    void afterAll() {
        context.stop();
    }

}
