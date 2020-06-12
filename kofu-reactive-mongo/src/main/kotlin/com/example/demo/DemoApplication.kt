package com.example.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.core.io.ClassPathResource
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.*
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.fu.kofu.configuration
import org.springframework.fu.kofu.mongo.reactiveMongodb
import org.springframework.fu.kofu.reactiveWebApplication
import org.springframework.fu.kofu.webflux.webFlux
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.net.URI
import java.time.LocalDateTime


val app = reactiveWebApplication {
    configurationProperties<SampleProperties>(prefix = "sample")
    enable(dataConfig)
    enable(webConfig)

    listener<ApplicationReadyEvent> {
        println("start data initialization...")
        ref<PostRepository>().init()
    }
    profile("foo") {
        beans { bean<Bar>() }
    }
}

class Bar

class SampleProperties(val message: String)

fun main() {
    app.run()
}

val dataConfig = configuration {
    reactiveMongodb {
    }

    beans {
        bean<PostRepository>()
    }

}


val webConfig = configuration {
    webFlux {
        port = if (profiles.contains("test")) 8181 else 8080
        codecs {
            string()
            jackson()
        }

        router {
            val postHandler = ref<PostHandler>()
            "posts".nest {
                GET("", postHandler::all)
                GET("count", postHandler::count)
                GET("{id}", postHandler::get)
                POST("", postHandler::create)
                PUT("{id}", postHandler::update)
                DELETE("{id}", postHandler::delete)
            }
        }
    }

    beans {
        bean<PostHandler>()
    }
}



class PostHandler(private val posts: PostRepository) {

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

    fun delete(req: ServerRequest): Mono<ServerResponse> {
        return this.posts.deleteById(req.pathVariable("id"))
                .flatMap { noContent().build() }
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
                        { println(it) },
                        { error -> println(error) },
                        { println("done") }
                )

    }
}

data class Count(val count: Long)

@Document
data class Post(
        @Id var id: String? = null,
        var title: String? = null,
        var content: String? = null,
        var createdDate: LocalDateTime = LocalDateTime.now()
)
