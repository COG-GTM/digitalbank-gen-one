package io.digisic.credit.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.digisic.credit.exception.RestInvalidArguementException;
import io.digisic.credit.model.security.Role;
import io.digisic.credit.service.UserSecurityService;
import io.digisic.credit.util.Constants;
import io.digisic.credit.util.Messages;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {
	
	private SecretKey secretKey;
	
	// Token expiration -> 1 hour (3600000)
	private long validityInMilliseconds = 3600000;
	
	@Autowired
	private UserSecurityService userSecurityService;
	  
	@PostConstruct
	protected void init() {
		secretKey = Keys.hmacShaKeyFor(Constants.API_SECRET.getBytes(StandardCharsets.UTF_8));
	}
	
	/*
	 * Create Token
	 */
	public String createToken(String username, List<Role> roles) {
	
		List<SimpleGrantedAuthority> auth = roles.stream().map(s -> new SimpleGrantedAuthority(s.getName())).filter(Objects::nonNull).collect(Collectors.toList());
	
	    Date now = new Date();
	    Date validity = new Date(now.getTime() + validityInMilliseconds);
	
	    return Jwts.builder()
	        .subject(username)
	        .claim("auth", auth)
	        .issuedAt(now)
	        .expiration(validity)
	        .signWith(secretKey, Jwts.SIG.HS256)
	        .compact();
	}
	
	/*
	 * Get Authentication
	 */
	public Authentication getAuthentication(String token) {
		
		UserDetails userDetails = userSecurityService.loadUserByUsername(getUsername(token));
	    
		return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
	}
	
	/*
	 * Get Username
	 */
	public String getUsername(String token) {
	    
		return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().getSubject();
	}
	
	/*
	 * Resolve Token 
	 */
	public String resolveToken(HttpServletRequest req) {
	
		String bearerToken = req.getHeader(Constants.API_AUTH_HEADER);
	
		if (bearerToken != null && bearerToken.startsWith(Constants.API_TOKEN_BEGIN)) {
			return bearerToken.substring(7);
	    }
	   
		return null;
	}
	
	/*
	 * Validate Token  
	 */
	public boolean validateToken(String token) {
		  
		try {
			  
			Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
			return true;
			  
	    } catch (JwtException | IllegalArgumentException e) {
	    	
	    	throw new RestInvalidArguementException (Messages.API_INVALID_TOKEN);
	    }
	}
	
}
