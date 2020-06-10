package com.example.demo;


import org.springframework.data.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.data.r2dbc.query.Criteria.where;


public class PostRepository {

    private final DatabaseClient databaseClient;

    public PostRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    Flux<Post> findByTitleContains(String name) {
        return this.databaseClient.select()
                .from(Post.class)
                .matching(where("title").like(name))
                .fetch()
                .all();
    }

    public Flux<Post> findAll() {
        return this.databaseClient.select()
                .from(Post.class)
                .fetch()
                .all();
    }

    public Mono<Post> findById(Integer id) {
        return this.databaseClient.select()
                .from(Post.class)
                .matching(where("id").is(id))
                .fetch()
                .one();
    }

    public Mono<Integer> save(Post p) {
        return this.databaseClient.insert().into(Post.class)
                .using(p)
                .fetch()
                .one()
                .map(m -> (Integer) m.get("id"));
    }

    public Mono<Integer> update(Post p) {
        return this.databaseClient.update()
                .table(Post.class)
                .using(p)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Integer> deleteById(Integer id) {
        return this.databaseClient.delete().from(Post.class)
                .matching(where("id").is(id))
                .fetch()
                .rowsUpdated();
    }

    public Mono<Void> deleteAll() {
        return this.databaseClient.execute("DELETE FROM posts").fetch().one().then();
    }

    public void init() {
        this.databaseClient.execute("CREATE TABLE IF NOT EXISTS posts (id SERIAL PRIMARY KEY, title VARCHAR(255), content VARCHAR(255));")
                .then()
                .then(deleteAll())
                .then(save(new Post("Learning Spring Boot", "content of learning Spring Boot")))
                .then(save(new Post("Learning Spring Fu", "content of learning Spring Fu")))
                .then().block();
    }
}