package com.mystores.service;

import com.mystores.entity.Product;
import com.mystores.repository.ProductRepository;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CatalogAssistantService {
    private static final int MAX_RESULTS = 20;
    private static final Set<String> BROWSE_TERMS = Set.of(
        "a", "about", "all", "and", "are", "available", "batao", "catalog", "catalogue", "dikhao",
        "do", "from", "groceries", "grocery", "hai", "hain", "have", "in", "item", "items", "ka", "kahan",
        "ke", "ki", "kya", "list", "mai", "mein", "me", "mujhe", "on", "or", "please", "product", "products",
        "shop", "show", "store", "saman", "samaan", "the", "to", "what", "which", "you", "kirana"
    );

    private final ProductRepository products;
    private final String aiApiKey;
    private final String aiModel;
    private final RestClient restClient;

    public CatalogAssistantService(ProductRepository products, Environment env, RestClient.Builder restClientBuilder) {
        this.products = products;
        this.aiApiKey = env.getProperty("app.ai.api-key", "").trim();
        this.aiModel = env.getProperty("app.ai.model", "gpt-4o-mini");
        this.restClient = restClientBuilder.baseUrl("https://api.openai.com/v1").build();
    }

    public Map<String, Object> ask(String rawQuestion) {
        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is required");

        List<Product> catalog = products.findByActiveTrue();
        if (catalog.isEmpty()) {
            return Map.of("answer", "Abhi catalog mein koi active product nahi hai.", "sources", List.of());
        }

        String normalizedQuestion = normalize(question);
        Set<String> terms = Arrays.stream(normalizedQuestion.split("\\s+"))
            .filter(term -> term.length() > 1 && !BROWSE_TERMS.contains(term))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean browseIntent = isBrowseQuestion(normalizedQuestion, terms);

        List<Product> matchingStores = catalog.stream()
            .filter(product -> containsAny(normalize(product.store.name), terms))
            .toList();

        List<Product> candidates;
        if (browseIntent) {
            // If a real store name was included, list that store's products; otherwise show the catalog.
            candidates = matchingStores.isEmpty() ? catalog : matchingStores;
        } else {
            candidates = catalog.stream()
                .filter(product -> matchesProduct(product, terms))
                .toList();
        }

        List<Product> found = candidates.stream().limit(MAX_RESULTS).toList();
        if (found.isEmpty()) {
            return Map.of("answer", "Catalog mein '" + question + "' se match hota product ya store nahi mila. Product ka naam ya category poochhein.", "sources", List.of());
        }

        String context = String.join("\n", found.stream().map(this::describe).toList());
        String answer = (browseIntent ? "Catalog mein ye products hain: " : "Catalog se mile products: ") + context;
        if (candidates.size() > MAX_RESULTS) {
            answer = "Catalog mein " + candidates.size() + " products hain; pehle " + MAX_RESULTS + " ye rahe: " + context;
        }
        if (!aiApiKey.isBlank()) answer = generateAnswer(question, context, answer);

        return Map.of("answer", answer, "sources", found.stream()
            .map(product -> Map.of("productId", product.id, "name", product.name, "store", product.store.name))
            .toList());
    }

    private boolean isBrowseQuestion(String question, Set<String> terms) {
        if (terms.isEmpty()) return true;
        if (question.matches(".*\\b(kya\\s+kya|kya\\s+hai|kya\\s+milta|kya\\s+milega|what\\s+do\\s+you\\s+have|what\\s+is\\s+available|show\\s+me|list\\s+the)\\b.*")) return true;
        return terms.stream().allMatch(BROWSE_TERMS::contains);
    }

    private boolean matchesProduct(Product product, Set<String> terms) {
        if (terms.isEmpty()) return true;
        String productText = normalize(product.name + " " + Objects.toString(product.description, ""));
        String storeName = normalize(product.store.name);
        return terms.stream().anyMatch(term -> productText.contains(term) || storeName.contains(term));
    }

    private boolean containsAny(String text, Set<String> terms) {
        return !terms.isEmpty() && terms.stream().anyMatch(text::contains);
    }

    private String describe(Product product) {
        return product.name + " | ₹" + product.price + " | " + (product.inStock() ? "In stock" : "Out of stock")
            + " | Store: " + product.store.name + " | " + Objects.toString(product.description, "No description");
    }

    private String generateAnswer(String question, String context, String fallback) {
        try {
            Map<?, ?> response = restClient.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + aiApiKey)
                .body(Map.of("model", aiModel, "messages", List.of(
                    Map.of("role", "system", "content", "You are My Stores shopping assistant. Answer in the user's language using only the catalog context. For broad questions, list the available products and their stores. If the context does not answer the question, say you could not find it. Do not invent prices or stock."),
                    Map.of("role", "user", "content", "Catalog context:\n" + context + "\n\nQuestion: " + question)
                )))
                .retrieve().body(Map.class);
            if (response == null) return fallback;
            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) return fallback;
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            Object content = message == null ? null : message.get("content");
            return content == null || content.toString().isBlank() ? fallback : content.toString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String normalize(String text) {
        String noMarks = Normalizer.normalize(Objects.toString(text, ""), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "");
        return noMarks.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
