package com.mystores.controller;

import com.mystores.entity.*;
import com.mystores.repository.*;
import com.mystores.service.PasswordResetService;
import com.mystores.service.SessionTokenService;
import com.mystores.service.CatalogAssistantService;
import com.mystores.service.MediaStorageService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;

record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
record RegisterRequest(@NotBlank String name, @Email @NotBlank String email, @NotBlank @Size(min=8) String password) {}
record ForgotPasswordRequest(@Email @NotBlank String email) {}
record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min=8) String password) {}
record StoreRequest(@NotBlank String name, String description, String location) {}
record ProductRequest(@NotBlank String name, String description, @NotNull @PositiveOrZero BigDecimal price,
                      @PositiveOrZero int stock, String imageUrl, List<String> imageUrls, String videoUrl, @NotNull Long storeId) {}
record AuthResponse(String token, String role, String name) {}
record ProductView(Long id,String name,String description,BigDecimal price,int stock,String imageUrl,List<String> imageUrls,String videoUrl,
                   Long storeId,String storeName,boolean inStock) {
    static ProductView of(Product p) { return new ProductView(p.id,p.name,p.description,p.price,p.stock,p.imageUrl,List.copyOf(p.imageUrls),p.videoUrl,p.store.id,p.store.name,p.inStock()); }
}

@RestController
@RequestMapping("/api")
public class Api {
    private final StoreRepository stores;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SellerAccountRepository sellers;
    private final OrderRepository orders;
    private final PasswordResetService passwordResets;
    private final SessionTokenService sessionTokens;
    private final CatalogAssistantService catalogAssistant;
    private final MediaStorageService mediaStorage;
    private final BCryptPasswordEncoder encoder;

    public Api(StoreRepository stores, ProductRepository products, CustomerRepository customers, SellerAccountRepository sellers,
        OrderRepository orders, PasswordResetService passwordResets, SessionTokenService sessionTokens, BCryptPasswordEncoder encoder,
        CatalogAssistantService catalogAssistant, MediaStorageService mediaStorage) {
        this.stores=stores; this.products=products; this.customers=customers; this.sellers=sellers; this.orders=orders;
        this.passwordResets=passwordResets; this.sessionTokens=sessionTokens; this.encoder=encoder; this.catalogAssistant=catalogAssistant; this.mediaStorage=mediaStorage;
    }

    @PostMapping("/auth/seller/login") AuthResponse sellerLogin(@Valid @RequestBody LoginRequest r) {
        SellerAccount owner=sellers.findByEmailIgnoreCase(r.email().trim()).orElseThrow(
            ()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect."));
        if(!encoder.matches(r.password(),owner.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        return session("SELLER",null,owner.name);
    }
    @GetMapping("/auth/session") Map<String,String> sessionStatus(@RequestHeader(value="X-Session-Token",required=false) String token) {
        return Map.of("role",sessionTokens.verify(token).role());
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

    private AuthResponse session(String role,Long id,String name){ return new AuthResponse(sessionTokens.issue(role,id),role,name); }
    private void seller(String token){ if(!sessionTokens.verify(token).role().equals("SELLER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Seller login required."); }
    private Customer customer(String token){ SessionTokenService.Principal p=sessionTokens.verify(token); if(!p.role().equals("CUSTOMER")||p.customerId()==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer login required."); return customers.findById(p.customerId()).orElseThrow(); }

    @GetMapping("/stores") List<Store> listStores(){ return stores.findByActiveTrue(); }
    @PostMapping("/seller/stores") Store createStore(@RequestHeader(value="X-Session-Token",required=false) String t,@Valid @RequestBody StoreRequest r){ seller(t); Store s=new Store(); s.name=r.name(); s.description=r.description(); s.location=r.location(); return stores.save(s); }
    @PutMapping("/seller/stores/{id}") Store editStore(@RequestHeader(value="X-Session-Token",required=false) String t,@PathVariable Long id,@Valid @RequestBody StoreRequest r){ seller(t); Store s=stores.findById(id).orElseThrow(); s.name=r.name(); s.description=r.description(); s.location=r.location(); return stores.save(s); }
    @DeleteMapping("/seller/stores/{id}") void deleteStore(@RequestHeader(value="X-Session-Token",required=false) String t,@PathVariable Long id){ seller(t); Store s=stores.findById(id).orElseThrow(); s.active=false; stores.save(s); products.findByStoreId(id).forEach(p->{p.active=false;products.save(p);}); }
    @GetMapping("/products") List<ProductView> listProducts(@RequestParam(required=false) Long storeId){ return (storeId==null?products.findByActiveTrue():products.findByActiveTrueAndStoreId(storeId)).stream().map(ProductView::of).toList(); }
    @PostMapping(value="/seller/media", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String,String> uploadMedia(@RequestHeader(value="X-Session-Token",required=false) String t,
                                   @RequestPart("file") MultipartFile file, @RequestParam String kind) {
        seller(t);
        if (!kind.equals("image") && !kind.equals("video")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Media kind must be image or video.");
        return Map.of("url", mediaStorage.store(file,kind));
    }
    @GetMapping("/media/{filename}")
    ResponseEntity<Resource> getMedia(@PathVariable String filename) throws Exception {
        Path path=mediaStorage.resolve(filename);
        String type=Files.probeContentType(path);
        MediaType mediaType=type==null?MediaType.APPLICATION_OCTET_STREAM:MediaType.parseMediaType(type);
        return ResponseEntity.ok().contentType(mediaType).body(new UrlResource(path.toUri()));
    }
    @PostMapping("/seller/products") ProductView createProduct(@RequestHeader(value="X-Session-Token",required=false) String t,@Valid @RequestBody ProductRequest r){ seller(t); Product p=new Product(); apply(p,r); return ProductView.of(products.save(p)); }
    @PutMapping("/seller/products/{id}") ProductView editProduct(@RequestHeader(value="X-Session-Token",required=false) String t,@PathVariable Long id,@Valid @RequestBody ProductRequest r){ seller(t); Product p=products.findById(id).orElseThrow(); apply(p,r); return ProductView.of(products.save(p)); }
    @DeleteMapping("/seller/products/{id}") void deleteProduct(@RequestHeader(value="X-Session-Token",required=false) String t,@PathVariable Long id){ seller(t); Product p=products.findById(id).orElseThrow(); p.active=false; products.save(p); }
    private void apply(Product p,ProductRequest r){ p.name=r.name(); p.description=r.description(); p.price=r.price(); p.stock=r.stock(); p.imageUrl=r.imageUrl(); if(r.imageUrls()!=null){p.imageUrls.clear();p.imageUrls.addAll(r.imageUrls());}else if(p.imageUrls.isEmpty()&&r.imageUrl()!=null&&!r.imageUrl().isBlank()){p.imageUrls.add(r.imageUrl());} p.videoUrl=r.videoUrl(); p.store=stores.findById(r.storeId()).filter(s->s.active).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found")); }
    @PostMapping("/orders") OrderRecord buy(@RequestHeader(value="X-Session-Token",required=false) String t,@RequestBody Map<String,Object> body){ Customer c=customer(t); Long id=Long.valueOf(body.get("productId").toString()); int quantity=Integer.parseInt(body.getOrDefault("quantity",1).toString()); if(quantity<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity must be positive"); Product p=products.findByIdAndActiveTrue(id).orElseThrow(); if(p.stock<quantity) throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock"); p.stock-=quantity; products.save(p); OrderRecord o=new OrderRecord(); o.customer=c; o.product=p; o.store=p.store; o.quantity=quantity; o.total=p.price.multiply(BigDecimal.valueOf(quantity)); return orders.save(o); }
    @GetMapping("/orders/mine") List<OrderRecord> myOrders(@RequestHeader(value="X-Session-Token",required=false) String t){ return orders.findByCustomerIdOrderByIdDesc(customer(t).id); }
    @PostMapping("/ai/ask") Map<String,Object> ask(@RequestBody Map<String,String> body){
        return catalogAssistant.ask(body.getOrDefault("question",""));
    }
}
