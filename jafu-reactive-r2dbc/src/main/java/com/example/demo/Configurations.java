package com.example.demo;

import org.springframework.fu.jafu.ConfigurationDsl;

import java.util.function.Consumer;

import static org.springframework.fu.jafu.r2dbc.R2dbcDsl.r2dbc;
import static org.springframework.fu.jafu.webflux.WebFluxServerDsl.webFlux;

public abstract class Configurations {

    public static Consumer<ConfigurationDsl> dataConfig = conf -> conf
            .beans(b -> b.bean(PostRepository.class))
            .enable(r2dbc(dsl -> dsl.url("r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1")));

    public static Consumer<ConfigurationDsl> webConfig = conf -> conf
            .beans(beans -> beans.bean(PostHandler.class))
            .enable(webFlux(server -> {
                if (conf.profiles().contains("test")) {
                    server.port(8181);
                } else {
                    server.port(8080);
                }
                server
                        .router(r -> {
                                    var postController = conf.ref(PostHandler.class);
                                    r.GET("/posts", postController::all)
                                            .POST("/posts", postController::create)
                                            .GET("/posts/{id}", postController::get)
                                            .PUT("/posts/{id}", postController::update)
                                            .DELETE("/posts/{id}", postController::delete);
                                }

                        )
                        .codecs(codecs -> codecs.string().jackson());
            }));

}
