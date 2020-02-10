package com.example.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.boot.WebApplicationType
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.core.io.ClassPathResource
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.*
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.event.ReactiveBeforeConvertCallback
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.isEqualTo

import org.springframework.fu.kofu.application
import org.springframework.fu.kofu.configuration
import org.springframework.fu.kofu.mongo.reactiveMongodb
import org.springframework.fu.kofu.webflux.webFlux
import org.springframework.http.HttpMethod
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.*
import org.springframework.web.reactive.function.server.router
import org.springframework.web.server.session.HeaderWebSessionIdResolver
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.net.URI
import java.time.LocalDateTime


val app = application(WebApplicationType.REACTIVE) {
    configurationProperties<SampleProperties>(prefix = "sample")
    enable(dataConfig)
    enable(webConfig)
}

fun main() {
    app.run()
}

val dataConfig = configuration {
    reactiveMongodb {
    }

    listener<ApplicationReadyEvent> {
        println("start data initialization...")
        ref<PostRepository>().init()
    }

    beans {
        bean<PostRepository>()
        bean<CommentRepository>()
        bean {
            ReactiveBeforeConvertCallback<PersistentEntity> { entity, collection ->
                println("ReactiveBeforeConvertCallback.onBeforeConvert called...")
                val user = ReactiveSecurityContextHolder.getContext()
                        .map { it.authentication }
                        .filter { it != null && it.isAuthenticated }
                        .map { it.principal }
                        .cast(UserDetails::class.java)
                        .map { Username(it.username) }
                        .switchIfEmpty { Mono.empty() }

                val currentTime = LocalDateTime.now()

                val ent = if (entity.id == null) {
                    entity.apply {
                        createdDate = currentTime
                        lastModifiedDate = currentTime
                    }

                } else {
                    entity.apply {
                        lastModifiedDate = currentTime
                    }
                }

                Mono.just(ent)
                        .flatMap { ent ->
                            user.map { user ->
                                if (ent.id == null) {
                                    ent.apply {
                                        createdBy = user
                                        lastModifiedBy = user
                                    }

                                } else {
                                    ent.apply {
                                        lastModifiedBy = user
                                    }
                                }
                            }.defaultIfEmpty(
                                    if (ent.id == null) {
                                        ent.apply {
                                            createdBy = null
                                            lastModifiedBy = null
                                        }
                                    } else {
                                        ent.apply {
                                            lastModifiedBy = null
                                        }

                                    }
                            )

                        }


            }
        }


    }
}


val webConfig = configuration {
    webFlux {
        port = if (profiles.contains("test")) 8181 else 8080
        codecs {
            string()
            jackson()
        }
    }

    beans {
        bean {
            PostHandler(ref(), ref())
        }

        bean<UserInfoHandler>()

        bean(::routes)
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

                CorsWebFilter(source)
            }
        }
    }
}

val sessionConfig = configuration {
    beans {
        bean {
            HeaderWebSessionIdResolver().apply {
                headerName = "X-AUTH-TOKEN"
            }
        }
    }
}

val securityConfig = configuration {
    beans {
        bean {
            PasswordEncoderFactories.createDelegatingPasswordEncoder()
        }

        bean<SecurityWebFilterChain> {
            //        val http = ref<ServerHttpSecurity>()
//        http {
//            csrf { disable() }
//            httpBasic { securityContextRepository = WebSessionServerSecurityContextRepository() }
//            authorizeExchange {
//                authorize("/auth/**", authenticated)
//                authorize(pathMatchers(HttpMethod.GET, "/posts/**"), permitAll)
//                authorize(pathMatchers(HttpMethod.DELETE, "/posts/**"), hasRole("ADMIN"))
//                authorize("/posts/**", authenticated)
//                authorize(anyExchange, permitAll)
//            }
//        }
            //@formatter:off
        ref<ServerHttpSecurity>()
                    .csrf { it.disable() }
                    .httpBasic{ it.securityContextRepository(WebSessionServerSecurityContextRepository())}
                    .authorizeExchange {
                        it.pathMatchers("/auth/**").authenticated()
                            .pathMatchers(HttpMethod.GET, "/posts/**").permitAll()
                            .pathMatchers(HttpMethod.DELETE, "/posts/**").hasRole("ADMIN")
                            .pathMatchers("/posts/**").authenticated()
                            .anyExchange().permitAll()
                    }
                .build()
        //@formatter:on
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
            MapReactiveUserDetailsService(user, admin)
        }
    }
}

fun routes(postHandler: PostHandler, userInfoHandler: UserInfoHandler) =
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

class SampleProperties(val message: String)

class UserInfoHandler {
    fun userInfo(req: ServerRequest): Mono<ServerResponse> {
        return req.principal()
                .map { user ->
                    mapOf<String, Any>(
                            "user" to user.name,
                            "roles" to (user as Authentication).authorities.map { it.authority })
                }
                .flatMap { ok().bodyValue(it) }
    }

    fun logout(req: ServerRequest): Mono<ServerResponse> {
        return req.session()
                .doOnNext { it.invalidate() }
                .log()
                .then()
                .flatMap { ok().build() }
    }
}

class PostHandler(private val posts: PostRepository, private val comments: CommentRepository) {

    fun all(req: ServerRequest): Mono<ServerResponse> {
        return ok().body(this.posts.findAll(), Post::class.java)
    }

    fun count(req: ServerRequest): Mono<ServerResponse> {
        return ok().body(this.posts.count().map { Count(count = it) }, Count::class.java)
    }

    fun create(req: ServerRequest): Mono<ServerResponse> {
        return req.bodyToMono(Post::class.java)
                .flatMap { this.posts.save(it) }
                .flatMap { created(URI.create("/posts/" + it.id)).build() }
    }

    fun get(req: ServerRequest): Mono<ServerResponse> {
        return this.posts.findById(req.pathVariable("id"))
                .flatMap { ok().body(Mono.just(it), Post::class.java) }
                .switchIfEmpty { notFound().build() }
    }

    fun update(req: ServerRequest): Mono<ServerResponse> {
        return this.posts.findById(req.pathVariable("id"))
                .zipWith(req.bodyToMono(Post::class.java))
                .map { it.t1.copy(title = it.t2.title, content = it.t2.content) }
                .flatMap { this.posts.save(it) }
                .flatMap { noContent().build() }
                .switchIfEmpty { notFound().build() }
    }

    fun updateStatus(req: ServerRequest): Mono<ServerResponse> {
        return this.posts.findById(req.pathVariable("id"))
                .zipWith(req.bodyToMono(UpdateStatusRequest::class.java))
                .map { it.t1.copy(status = it.t2.status) }
                .flatMap { this.posts.save(it) }
                .flatMap { noContent().build() }
                .switchIfEmpty { notFound().build() }
    }

    fun delete(req: ServerRequest): Mono<ServerResponse> {
        return this.posts.deleteById(req.pathVariable("id"))
                .flatMap { noContent().build() }
    }

    fun createComment(req: ServerRequest): Mono<ServerResponse> {
        val id = req.pathVariable("id")
        val postId = PostId(id)
        return Mono.just(Comment(post = postId))
                .zipWith(req.bodyToMono(CommentForm::class.java))
                .map { it.t1.copy(content = it.t2.content) }
                .flatMap { this.comments.save(it) }
                .flatMap { created(URI.create("/posts/" + id + "/comments" + it.id)).build() }
    }

    fun countCommentsOfPost(req: ServerRequest): Mono<ServerResponse> {
        val id = req.pathVariable("id")
        val postId = PostId(id)
        return ok().body(this.comments.findByPost(postId).count().map { Count(count = it) }, Count::class.java)
    }

    fun getCommentsOfPost(req: ServerRequest): Mono<ServerResponse> {
        val id = req.pathVariable("id")
        val postId = PostId(id)

        return ok().body(this.comments.findByPost(postId), Comment::class.java)
    }
}

class PostRepository(private val mongo: ReactiveMongoOperations, private val objectMapper: ObjectMapper) {
    fun count() = mongo.count<Post>()

    fun findAll() = mongo.findAll<Post>()

    fun findById(id: String) = mongo.findById<Post>(id)

    fun deleteAll() = mongo.remove<Post>().all()

    fun save(post: Post) = mongo.save(post)

    fun deleteById(id: String) = mongo.remove<Post>(query(where("id").isEqualTo(id)))

    fun init() {
        val postsResource = ClassPathResource("data/posts.json")
        val posts: List<Post> = objectMapper.readValue(postsResource.inputStream)
        deleteAll()
                .thenMany(
                    Flux.fromIterable(posts).flatMap { save(it) }
                )
                .log()
                .subscribe(
                        { it -> println(it) },
                        { error -> println(error) },
                        { println("done") }
                )

    }
}

class CommentRepository(private val mongo: ReactiveMongoOperations) {

    fun save(comment: Comment) = mongo.save(comment)

    fun countByPost(postId: PostId) =
            mongo.count<Comment>(query(where("post").isEqualTo(postId)))


    fun findByPost(postId: PostId) =
            mongo.find<Comment>(query(where("post").isEqualTo(postId)))
}

//class PostNotFoundException(private val postId: String) : RuntimeException(String.format("Post: %s is not found", postId))
data class Username(var username: String? = null)

data class PostId(var id: String? = null)

data class UpdateStatusRequest(var status: Status = Status.DRAFT)

data class CommentForm(var content: String? = null)

data class Count(val count: Long)

interface PersistentEntity {
    var id: String?
    var createdDate: LocalDateTime
    var createdBy: Username?
    var lastModifiedDate: LocalDateTime
    var lastModifiedBy: Username?
}

@Document
data class Post(
        @Id override var id: String? = null,
        var title: String? = null,
        var content: String? = null,
        val status: Status = Status.DRAFT,
        override var createdDate: LocalDateTime = LocalDateTime.now(),
        override var lastModifiedDate: LocalDateTime = createdDate,
        override var createdBy: Username? = null,
        override var lastModifiedBy: Username? = null
) : PersistentEntity

enum class Status {
    DRAFT, PUBLISHED
}

@Document
data class Comment(
        @Id override var id: String? = null,
        var content: String? = null,
        var post: PostId,
        override var createdDate: LocalDateTime = LocalDateTime.now(),
        override var lastModifiedDate: LocalDateTime = createdDate,
        override var createdBy: Username? = null,
        override var lastModifiedBy: Username? = null
) : PersistentEntity


