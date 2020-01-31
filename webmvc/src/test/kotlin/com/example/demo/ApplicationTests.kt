package com.example.demo


import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse


@SpringBootTest(classes = arrayOf(DemoApplication::class),
        properties = ["context.initializer.classes=com.example.demo.TestConfigInitializer"])
class ApplicationTests {

    @Autowired
    private lateinit var ctx: WebApplicationContext

    lateinit var mockMvc: MockMvc


    @BeforeAll
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build()
    }


    @Test
    fun `get all posts`() {
        mockMvc
                .get("/posts")
                .andExpect {
                    status {
                        isOk
                    }
                }
    }

}
