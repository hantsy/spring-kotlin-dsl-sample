package com.example.demo;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.fu.jafu.JafuApplication;

import static com.example.demo.Configurations.dataConfig;
import static com.example.demo.Configurations.webConfig;
import static org.springframework.fu.jafu.Jafu.reactiveWebApplication;

public abstract class Application {

    public static JafuApplication app = reactiveWebApplication(app -> app
            .enable(dataConfig)
            .enable(webConfig)
            .listener(ApplicationReadyEvent.class, e -> app.ref(PostRepository.class).init()));

    public static void main(String[] args) {
        app.run(args);
    }

}
