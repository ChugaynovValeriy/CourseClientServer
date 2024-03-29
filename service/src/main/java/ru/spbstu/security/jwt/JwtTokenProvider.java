package ru.spbstu.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.spbstu.entity.User;
import ru.spbstu.exception.InvalidJwtAuthenticationException;
import ru.spbstu.exception.InvalidPasswordException;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {
    
    private final JwtProperties jwtProperties;
    
    private final UserDetailsService userDetailsService;
    
    private final PasswordEncoder passwordEncoder;
    
    @Autowired
    public JwtTokenProvider(JwtProperties jwtProperties,
                            @Qualifier("customUserDetailService") UserDetailsService userDetailsService,
                            PasswordEncoder passwordEncoder) {
        this.jwtProperties = jwtProperties;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }
    
    private String secretKey;
    
    @PostConstruct
    private void init() {
        secretKey = Base64.getEncoder().encodeToString(jwtProperties.getSecretKey().getBytes());
    }
    
    public String createToken(String userName, String password, List<String> roles) {
        if (userName == null || password == null) {
            return null;
        }
        User user = (User) userDetailsService.loadUserByUsername(userName);
        if (passwordEncoder.matches(password, user.getPassword())) {
            Claims claims = Jwts.claims().setSubject(userName);
            claims.put("roles", roles);
            
            Date now = new Date();
            Date validity = new Date(now.getTime() + jwtProperties.getValidTime());
            
            return Jwts.builder()
                    .setClaims(claims)
                    .setIssuedAt(now)
                    .setExpiration(validity)
                    .signWith(SignatureAlgorithm.HS256, secretKey)
                    .compact();
        } else {
            throw new InvalidPasswordException("Password is invalid");
        }
    }
    
    public String getUserName(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
    }
    
    public boolean validateToken(String token) {
        try {
            Jws<Claims> claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException a) {
            throw new InvalidJwtAuthenticationException("Expired or invalid token");
        }
    }
    
    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    public Authentication getAuthentication(String token) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(getUserName(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
}