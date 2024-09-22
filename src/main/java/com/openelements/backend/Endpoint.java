package com.openelements.backend;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api")
public class Endpoint {

    private final static Logger log = org.slf4j.LoggerFactory.getLogger(Endpoint.class);

    @GetMapping("/items")
    public Set<Item> getItems() {
        log.info("Getting items");
        final Random random = new Random(System.currentTimeMillis());
        return IntStream.range(0, random.nextInt(25))
                .mapToObj(i -> new Item(String.valueOf(i), "Item " + i))
                .collect(Collectors.toUnmodifiableSet());
    }
}
