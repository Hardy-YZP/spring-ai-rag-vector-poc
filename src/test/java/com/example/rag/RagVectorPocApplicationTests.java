package com.example.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.profiles.active=local-hash",
        "server.port=0",
        "rag.vector-store.file=target/test-data/context-vector-store.json",
        "rag.document.manifest-file=target/test-data/context-embedded-files.tsv"
})
class RagVectorPocApplicationTests {

    @Test
    void contextLoads() {
    }
}
