package com.example.demo


import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext


@SpringBootTest(
    classes = [DemoApplication::class],
    properties = ["context.initializer.classes=com.example.demo.TestConfigInitializer"]
)
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
                    isOk()
                }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                }
                jsonPath("length()", Matchers.greaterThan(0))
            }
    }

}
