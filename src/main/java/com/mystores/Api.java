package com.mystores;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
record RegisterRequest(@NotBlank String name, @Email @NotBlank String email, @NotBlank @Size(min=8) String password) {}
record ForgotPasswordRequest(@Email @NotBlank String email) {}
record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min=8) String password) {}
record StoreRequest(@NotBlank String name, String description, String location) {}
record ProductRequest(@NotBlank String name, String description, @NotNull @PositiveOrZero BigDecimal price,
                      @PositiveOrZero int stock, String imageUrl, @NotNull Long storeId) {}
record AuthResponse(String token, String role, String name) {}
record SessionPrincipal(String role, Long customerId) {}
record ProductView(Long id,String name,String description,BigDecimal price,int stock,String imageUrl,
                   Long storeId,String storeName,boolean inStock) {
    static ProductView of(Product p) { return new ProductView(p.id,p.name,p.description,p.price,p.stock,p.imageUrl,p.store.id,p.store.name,p.inStock()); }
}

@RestController
@RequestMapping("/api")
class Api {
    private final StoreRepository stores;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SellerAccountRepository sellers;
    private final OrderRepository orders;
    private final PasswordResetService passwordResets;
    private final BCryptPasswordEncoder encoder;
    private final String aiApiKey, aiModel;
    private final org.springframework.web.client.RestClient restClient;
    private final Map<String,SessionPrincipal> sessions = new ConcurrentHashMap<>();

    Api(StoreRepository stores, ProductRepository products, CustomerRepository customers, SellerAccountRepository sellers,
        OrderRepository orders, PasswordResetService passwordResets, BCryptPasswordEncoder encoder,
        org.springframework.core.env.Environment env, org.springframework.web.client.RestClient.Builder restClientBuilder) {
        this.stores=stores; this.products=products; this.customers=customers; this.sellers=sellers; this.orders=orders;
        this.passwordResets=passwordResets; this.encoder=encoder;
        aiApiKey=env.getProperty("app.ai.api-key",""); aiModel=env.getProperty("app.ai.model","gpt-4o-mini");
        restClient=restClientBuilder.baseUrl("https://api.openai.com/v1").build();
    }

    @PostMapping("/auth/seller/login") AuthResponse sellerLogin(@Valid @RequestBody LoginRequest r) {
        SellerAccount owner=sellers.findByEmailIgnoreCase(r.email().trim()).orElseThrow(
            ()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect."));
        if(!encoder.matches(r.password(),owner.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        return session("SELLER",null,owner.name);
    }
    @PostMapping("/auth/customer/register") AuthResponse register(@Valid @RequestBody RegisterRequest r) {
        if(customers.findByEmailIgnoreCase(r.email().trim()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered.");
        Customer c=new Customer(); c.email=r.email().trim().toLowerCase(Locale.ROOT); c.name=r.name().trim();
        c.passwordHash=encoder.encode(r.password()); c.passwordChangedAt=Instant.now(); customers.save(c);
        return session("CUSTOMER",c.id,c.name);
    }
    @PostMapping("/auth/customer/login") AuthResponse customerLogin(@Valid @RequestBody LoginRequest r) {
        Customer c=customers.findByEmailIgnoreCase(r.email().trim()).orElseThrow(
            ()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect."));
        if(!encoder.matches(r.password(),c.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        return session("CUSTOMER",c.id,c.name);
    }
    @PostMapping("/auth/{role}/forgot-password") Map<String,String> forgotPassword(@PathVariable String role,@Valid @RequestBody ForgotPasswordRequest r) {
        passwordResets.requestReset(role,r.email());
        return Map.of("message","If this account exists, password reset instructions have been sent.");
    }
    @PostMapping("/auth/reset-password") Map<String,String> resetCustomerPassword(@Valid @RequestBody ResetPasswordRequest r) {
        passwordResets.resetPassword(r.token(),"CUSTOMER",r.password());
        return Map.of("message","Password updated. Sign in with your new password.");
    }
    @PostMapping("/auth/seller/reset-password") Map<String,String> resetSellerPassword(@Valid @RequestBody ResetPasswordRequest r) {
        passwordResets.resetPassword(r.token(),"SELLER",r.password());
        return Map.of("message","Password updated. Sign in with your new password.");
    }

    private AuthResponse session(String role,Long id,String name){ String t=UUID.randomUUID().toString(); sessions.put(t,new SessionPrincipal(role,id)); return new AuthResponse(t,role,name); }
    private void seller(String token){ SessionPrincipal p=token==null?null:sessions.get(token); if(p==null||!p.role().equals("SELLER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Seller login required."); }
    private Customer customer(String token){ SessionPrincipal p=token==null?null:sessions.get(token); if(p==null||!p.role().equals("CUSTOMER")||p.customerId()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Customer login required."); return customers.findById(p.customerId()).orElseThrow(); }

    @GetMapping("/stores") List<Store> listStores(){ return stores.findByActiveTrue(); }
    @PostMapping("/seller/stores") Store createStore(@RequestHeader(value="X-Session-Token",required=false) String t,@Valid @RequestBody StoreRequest r){ seller(t); Store s=new Store(); s.name=r.name(); s.description=r.description(); s.location=r.location(); return stores.save(s); }
    @PutMapping("/seller/stores/{id}") Store editStore(@RequestHeader(value="X-Session-Token",required=false) String t,@PathVariable Long id,@Valid @RequestBody StoreRequest r){ seller(t); Store s=stores.findById(id).orElseThrow(); s.name=r.name(); s.description=r.description(); s.location=r.location(); return stores.save(s); }
    @DeleteMapping("/seller/stores/{id}") void deleteStore(@RequestHeader(value="X-Session-Token",required=false) String t,@PathVariable Long id){ seller(t); Store s=stores.findById(id).orElseThrow(); s.active=false; stores.save(s); products.findByStoreId(id).forEach(p->{p.active=false;products.save(p);}); }
    @GetMapping("/products") List<ProductView> listProducts(@RequestParam(required=false) Long storeId){ return (storeId==null?products.findByActiveTrue():products.findByActiveTrueAndStoreId(storeId)).stream().map(ProductView::of).toList(); }
    @PostMapping("/seller/products") ProductView createProduct(@RequestHeader(value="X-Session-Token",required=false) String t,@Valid @RequestBody ProductRequest r){ seller(t); Product p=new Product(); apply(p,r); return ProductView.of(products.save(p)); }
    @PutMapping("/seller/products/{id}") ProductView editProduct(@RequestHeader(value="X-Session-Token",required=false) String t,@PathVariable Long id,@Valid @RequestBody ProductRequest r){ seller(t); Product p=products.findById(id).orElseThrow(); apply(p,r); return ProductView.of(products.save(p)); }
    @DeleteMapping("/seller/products/{id}") void deleteProduct(@RequestHeader(value="X-Session-Token",required=false) String t,@PathVariable Long id){ seller(t); Product p=products.findById(id).orElseThrow(); p.active=false; products.save(p); }
    private void apply(Product p,ProductRequest r){ p.name=r.name(); p.description=r.description(); p.price=r.price(); p.stock=r.stock(); p.imageUrl=r.imageUrl(); p.store=stores.findById(r.storeId()).filter(s->s.active).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found")); }
    @PostMapping("/orders") OrderRecord buy(@RequestHeader(value="X-Session-Token",required=false) String t,@RequestBody Map<String,Object> body){ Customer c=customer(t); Long id=Long.valueOf(body.get("productId").toString()); int quantity=Integer.parseInt(body.getOrDefault("quantity",1).toString()); if(quantity<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity must be positive"); Product p=products.findByIdAndActiveTrue(id).orElseThrow(); if(p.stock<quantity) throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock"); p.stock-=quantity; products.save(p); OrderRecord o=new OrderRecord(); o.customer=c; o.product=p; o.store=p.store; o.quantity=quantity; o.total=p.price.multiply(BigDecimal.valueOf(quantity)); return orders.save(o); }
    @GetMapping("/orders/mine") List<OrderRecord> myOrders(@RequestHeader(value="X-Session-Token",required=false) String t){ return orders.findByCustomerIdOrderByIdDesc(customer(t).id); }
    @PostMapping("/ai/ask") Map<String,Object> ask(@RequestBody Map<String,String> body){
        String question=body.getOrDefault("question","").trim(); if(question.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Question is required");
        String q=question.toLowerCase(); List<Product> catalog=products.findByActiveTrue();
        List<Product> found=catalog.stream().filter(p->Arrays.stream(q.split("\\W+")).filter(w->w.length()>2).anyMatch(w->(p.name+" "+Objects.toString(p.description,"")+" "+p.store.name).toLowerCase().contains(w))).limit(5).toList();
        if(found.isEmpty()) return Map.of("answer","Mujhe catalog mein matching product nahi mila. Product name ya store ke baare mein poochhein.","sources",List.of());
        String context=String.join("\n",found.stream().map(p->p.name+" | ₹"+p.price+" | "+(p.inStock()?"In stock":"Out of stock")+" | Store: "+p.store.name+" | "+Objects.toString(p.description,"No description")).toList());
        String answer="Catalog se mile products: "+context;
        if(!aiApiKey.isBlank()) try {
            Map<?,?> response=restClient.post().uri("/chat/completions").header("Authorization","Bearer "+aiApiKey).body(Map.of("model",aiModel,"messages",List.of(Map.of("role","system","content","You are My Stores shopping assistant. Answer in the user's language using only the catalog context. If the context does not answer the question, say you could not find it. Do not invent prices or stock."),Map.of("role","user","content","Catalog context:\n"+context+"\n\nQuestion: "+question)))).retrieve().body(Map.class);
            if(response!=null){ List<?> choices=(List<?>)response.get("choices"); if(choices!=null&&!choices.isEmpty()){ Map<?,?> choice=(Map<?,?>)choices.get(0); Map<?,?> message=(Map<?,?>)choice.get("message"); if(message!=null&&message.get("content")!=null) answer=message.get("content").toString(); } }
        } catch(Exception ignored) { /* Preserve retrieved catalog results if the AI service is unavailable. */ }
        return Map.of("answer",answer,"sources",found.stream().map(p->Map.of("productId",p.id,"name",p.name,"store",p.store.name)).toList());
    }
}
