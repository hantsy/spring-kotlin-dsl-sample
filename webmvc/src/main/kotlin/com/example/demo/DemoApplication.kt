package com.example.demo

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.support.beans
import org.springframework.data.annotation.*
import org.springframework.data.domain.AuditorAware
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.web.servlet.invoke
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession
import org.springframework.session.web.http.HeaderHttpSessionIdResolver
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.ServerResponse.*
import org.springframework.web.servlet.function.router
import java.net.URI
import java.time.LocalDateTime
import java.util.*
import java.util.stream.Stream
import kotlin.reflect.full.cast


@SpringBootApplication
@EnableMongoHttpSession
@EnableMongoAuditing
class DemoApplication


val beans = beans {
    bean {
        CommandLineRunner {
            println("start data initialization...")
            val posts = ref<PostRepository>()

            val data = arrayListOf(
                    Post(null, "my first post", "content of my first post"),
                    Post(null, "my second post", "content of my second post")
            )
            posts.saveAll(data)
            posts.findAll().forEach {
                println("post data::$it")
            }

            println("data initialization done...")
        }
    }


    bean {
        PostHandler(ref(), ref())
    }

    bean<UserInfoHandler>()


    bean {
        HeaderHttpSessionIdResolver.xAuthToken()
    }

    profile("cors") {
        bean("corsFilter") {

            //val config = CorsConfiguration().apply {
            // allowedOrigins = listOf("http://allowed-origin.com")
            // maxAge = 8000L
            // addAllowedMethod("PUT")
            // addAllowedHeader("X-Allowed")
            //}

            val config = CorsConfiguration().applyPermitDefaultValues()

            val source = UrlBasedCorsConfigurationSource().apply {
                registerCorsConfiguration("/**", config)
            }

            CorsFilter(source)
        }
    }

    bean {
        PasswordEncoderFactories.createDelegatingPasswordEncoder()
    }

    bean {
        AuditorAware<Username> {
            Optional.ofNullable(SecurityContextHolder.getContext())
                    .map { it.authentication }
                    .filter { it.isAuthenticated }
                    .map { UserDetails::class.cast(it) }
                    .map { Username(it.username) }
        }
    }
    //https://github.com/spring-projects/spring-security/issues/7961#issuecomment-700005879
//    bean<WebSecurityConfigurerAdapter> {
//        val config = object : WebSecurityConfigurerAdapter() {
//            override fun configure(http: HttpSecurity?) {
//                http {
//                    csrf { disable() }
//                    httpBasic { }
//                    securityMatcher("/**")
//                    authorizeRequests {
//                        authorize("/auth/**", authenticated)
//                        authorize(AntPathRequestMatcher("/posts/**", HttpMethod.GET.name), permitAll)
//                        authorize(AntPathRequestMatcher("/posts/**", HttpMethod.DELETE.name), "hasRole('ADMIN')")
//                        authorize("/posts/**", authenticated)
//                        authorize(anyRequest, permitAll)
//                    }
//                }
//            }
//        }
//        config
//    }

    bean {
        val http = ref<HttpSecurity>()
        http {
            csrf { disable() }
            httpBasic { }
            securityMatcher("/**")
            authorizeRequests {
                authorize("/auth/**", authenticated)
                authorize(AntPathRequestMatcher("/posts/**", HttpMethod.GET.name), permitAll)
                authorize(HttpMethod.DELETE, "/posts/**", hasRole("ADMIN"))
                authorize("/posts/**", authenticated)
                authorize(anyRequest, permitAll)
            }
//            formLogin {
//                loginPage = "/log-in"
//            }
        }
        http.build()
    }

    bean {
        val postHandler = ref<PostHandler>()
        val userInfoHandler = ref<UserInfoHandler>()
        router {
            "posts".nest {
                GET("", postHandler::all)
                GET("count", postHandler::count)
                GET("{id}", postHandler::get)
                POST("", postHandler::create)
                PUT("{id}", postHandler::update)
                PATCH("{id}", postHandler::updateStatus)
                DELETE("{id}", postHandler::delete)

                //comments
                "{id}/comments".nest {
                    GET("count", postHandler::countCommentsOfPost)
                    GET("", postHandler::getCommentsOfPost)
                    POST("", postHandler::createComment)
                }
            }
            //get user info
            "/auth".nest {
                GET("/user", userInfoHandler::userInfo)
                GET("/logout", userInfoHandler::logout)
            }
        }
    }

    bean {
        val passwordEncoder = ref<PasswordEncoder>()
        val user = User.withUsername("user")
                .passwordEncoder { passwordEncoder.encode(it) }
                .password("password")
                .roles("USER").build()
        val admin = User.withUsername("admin")
                .password("password")
                .passwordEncoder { passwordEncoder.encode(it) }
                .roles("USER", "ADMIN")
                .build()
        InMemoryUserDetailsManager(user, admin)
    }

}


fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args) {
        addInitializers(beans)
    }
}

class UserInfoHandler {
    fun userInfo(req: ServerRequest): ServerResponse {
        return req.principal()
                .map { user ->
                    mapOf(
                            "user" to user.name,
                            "roles" to (user as Authentication).authorities.map { it.authority })
                }
                .map { ok().body(it) }
                .orElse(status(HttpStatus.FORBIDDEN).build())
    }

    fun logout(req: ServerRequest): ServerResponse {
        req.session().invalidate()
        return ok().build()
    }
}

class PostHandler(private val posts: PostRepository, private val comments: CommentRepository) {

    fun all(req: ServerRequest): ServerResponse {
        return ok().body(this.posts.findAll())
    }

    fun count(req: ServerRequest): ServerResponse {
        return ok().body(Count(this.posts.count()))
    }

    fun create(req: ServerRequest): ServerResponse {
        val saved = this.posts.save(req.body(Post::class.java))
        return created(URI.create("/posts/" + saved.id)).build()
    }

    fun get(req: ServerRequest): ServerResponse {
        return this.posts.findById(req.pathVariable("id"))
                .map { ok().body(it) }
                .orElse(notFound().build())
    }

    fun update(req: ServerRequest): ServerResponse {
        val data = req.body(Post::class.java)

        return this.posts.findById(req.pathVariable("id"))
                .map { it.copy(title = data.title, content = data.content) }
                .map { this.posts.save(it) }
                .map { noContent().build() }
                .orElse(notFound().build())

    }

    fun updateStatus(req: ServerRequest): ServerResponse {
        val data = req.body(UpdateStatusRequest::class.java)

        return this.posts.findById(req.pathVariable("id"))
                .map { it.copy(status = data.status) }
                .map { this.posts.save(it) }
                .map { noContent().build() }
                .orElse(notFound().build())
    }

    fun delete(req: ServerRequest): ServerResponse {
        return this.posts.findById(req.pathVariable("id"))
                .map {
                    this.posts.delete(it)
                    noContent().build()
                }
                .orElse(notFound().build())
    }

    fun createComment(req: ServerRequest): ServerResponse {
        val id = req.pathVariable("id")
        val comment = Comment(post = PostId(id), content = req.body(CommentForm::class.java).content)
        val saved = this.comments.save(comment)
        return created(URI.create("/posts/" + id + "/comments" + saved.id)).build()
    }

    fun countCommentsOfPost(req: ServerRequest): ServerResponse {
        val id = req.pathVariable("id")
        val postId = PostId(id)
        return ok().body(Count(count = this.comments.countByPost(postId)))
    }

    fun getCommentsOfPost(req: ServerRequest): ServerResponse {
        val id = req.pathVariable("id")
        val postId = PostId(id)

        return ok().body(this.comments.findByPost(postId))
    }
}


//class PostNotFoundException(private val postId: String) : RuntimeException(String.format("Post: %s is not found", postId))
data class Username(var username: String? = null)

data class PostId(var id: String? = null)

data class UpdateStatusRequest(var status: Status = Status.DRAFT)

data class CommentForm(var content: String? = null)

data class Count(val count: Long)

@Document
data class Post(
        @Id var id: String? = null,
        var title: String? = null,
        var content: String? = null,
        val status: Status = Status.DRAFT,
        @CreatedDate var createdDate: LocalDateTime? = null,
        @LastModifiedDate var lastModifiedDate: LocalDateTime? = null,
        @CreatedBy var createdBy: Username? = null,
        @LastModifiedBy var lastModifiedBy: Username? = null
)

enum class Status {
    DRAFT, PUBLISHED
}

interface PostRepository : MongoRepository<Post, String>

@Document
data class Comment(
        @Id var id: String? = null,
        var content: String? = null,
        var post: PostId,
        @CreatedDate var createdDate: LocalDateTime? = null,
        @LastModifiedDate var lastModifiedDate: LocalDateTime? = null,
        @CreatedBy var createdBy: Username? = null,
        @LastModifiedBy var lastModifiedBy: Username? = null
)

interface CommentRepository : MongoRepository<Comment, String> {
    fun findByPost(id: PostId): Stream<Comment>
    fun countByPost(id: PostId): Long
}
