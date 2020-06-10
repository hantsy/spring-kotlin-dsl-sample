package com.example.demo;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.fu.jafu.ConfigurationDsl;
import org.springframework.fu.jafu.JafuApplication;

import java.util.function.Consumer;

import static org.springframework.fu.jafu.Jafu.reactiveWebApplication;
import static org.springframework.fu.jafu.r2dbc.H2R2dbcDsl.r2dbcH2;
import static org.springframework.fu.jafu.webflux.WebFluxServerDsl.webFlux;

@SpringBootApplication
public class Application {

    public static Consumer<ConfigurationDsl> dataConfig = conf -> conf
            .beans(b -> b.bean(PostRepository.class)).enable(r2dbcH2());

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

    public static JafuApplication app = reactiveWebApplication(app -> app
            .enable(dataConfig)
            .enable(webConfig)
            .listener(ApplicationReadyEvent.class, e -> app.ref(PostRepository.class).init()));

    public static void main(String[] args) {
        app.run(args);
    }

}
