package com.backend.server.security;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties.Lettuce.Cluster.Refresh;
import org.springframework.stereotype.Service;

// import com.backend.server.companies.CompanyRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.backend.server.companies.CompAppEmailsRepository;
import com.backend.server.companies.Company;
import com.backend.server.companies.CompanyApprovedEmails;

import com.backend.server.security.DTO.UpdateDTO;
import com.backend.server.users.User;
import com.backend.server.users.UserRepository;
import com.backend.server.utility.LoginResponse;
import com.backend.server.utility.Role;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class SecurityService {
    private final UserRepository userRepository;
    // private final CompanyRepository companyRepository;   // tarviikohan?
    private final CompAppEmailsRepository companyApprovedEmailsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Encoder encoder;

    @Value("${jwt.secret}") // application.properties
    private String jwtKey;

    @Value("${jwt.refreshToken.expirationTime}")
    private String refreshTokenExpirationTimeString;

    @Value("${jwt.accessToken.expirationTime}")
    private String accessTokenExpirationTimeString;

    private Long refreshTokenExpirationTime;
    private Long accessTokenExpirationTime;

    @PostConstruct   // ajat on app propertiesissa ja tulee stringinä, täytyy kääntää jotta toimii token funktioissa
    public void init() {
        refreshTokenExpirationTime = Long.parseLong(refreshTokenExpirationTimeString);
        accessTokenExpirationTime = Long.parseLong(accessTokenExpirationTimeString);
    }

    @Transactional  // overload koska puhelinnumero ei ole pakollinen
    public User register(String email, String password, String firstname, String lastname) {
        return register(email, password, firstname, lastname, null); // kutsu tyhjällä puhelinnumerolla
    }

    @Transactional
    public User register(String email, String password, String firstname, String lastname, String phoneNumber) {
        // null check
        if(email == null || password == null) {
            throw new IllegalArgumentException("All fields must be filled");
        }

        // tarkista onko sähköposti sallitulla listalla
        if(!isEmailApproved(email)){
            throw new IllegalArgumentException("Email is not on any approved list");
        }

        // tarkista jos käyttäjällä on jo tili
        if(userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        // hae rooli
        Role role = companyApprovedEmailsRepository.findByEmail(email).get().getRole();


        // hae company jolla sähköposti on listoilla
        CompanyApprovedEmails approvedEmail = companyApprovedEmailsRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Email is not on any approved list"));

        Company company = approvedEmail.getCompany();


        String encodedPassword = encoder.encode(password);

        User user = new User();
        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setFirstName(firstname);
        user.setLastName(lastname);
        user.setRole(role);  // asetetaan emailista haettu rooli
        if (phoneNumber != null) {
            user.setPhoneNumber(phoneNumber);
        }
        user.setCompany(company);

        return userRepository.save(user); 
    }


    @Transactional User updateUserDetails(User currentUser, UpdateDTO dto, Boolean requesterIsMaster, Boolean updatingSelf){
        User targetUser = currentUser;
        if (dto.getEmail() != null && requesterIsMaster) {
            // tarkista onko uusi sähköposti varattuna hyväksytyissä sähköposteissa
            if (isEmailApproved(dto.getEmail())) {
                throw new IllegalArgumentException("New email in use in approved emails");
            }
            // tarkista onko sähköposti varattu olemassaolevissa käyttäjissä
            if(userRepository.findByEmail(dto.getEmail()).isPresent()) {
                throw new IllegalArgumentException("New email already in use");
            }
            updateApprovedEmails(targetUser.getEmail(), dto.getEmail());
            targetUser.setEmail(dto.getEmail());
            
        }
        // katso onko rooli muuttunut ja tarkista onko muuttaja master, ja tarkista ettet muuta omaa rooliasi
        if (dto.getRole() != null && requesterIsMaster && !updatingSelf) {
            targetUser.setRole(dto.getRole());
        }

        if (dto.getFirstName() != null) {
            targetUser.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            targetUser.setLastName(dto.getLastName());
        }
        if (dto.getPhoneNumber() != null) {
            targetUser.setPhoneNumber(dto.getPhoneNumber());
        }

        return userRepository.save(targetUser);
    }
    
    @Transactional
    private void updateApprovedEmails(String oldEmail, String newEmail) {
        Optional<CompanyApprovedEmails> oldApprovedEmail = companyApprovedEmailsRepository.findByEmail(oldEmail);
        if (oldApprovedEmail.isPresent()) {
            CompanyApprovedEmails approvedEmail = oldApprovedEmail.get();
            approvedEmail.setEmail(newEmail);
            companyApprovedEmailsRepository.save(approvedEmail);
        }
    }

    @Transactional
    public void deleteUserAndApprovedEmail(User targetUser, CompanyApprovedEmails approvedEmail) {
        userRepository.delete(targetUser);
        companyApprovedEmailsRepository.delete(approvedEmail);
        String errorMessage = ""; // tarkista onnistuiko poisto, jos ei, heitä virhe
        if (userRepository.findByEmail(targetUser.getEmail()).isPresent()) {
            errorMessage = "User deletion failed.";
        } else if (companyApprovedEmailsRepository.findByEmail(approvedEmail.getEmail()).isPresent()) {
            errorMessage += " Approved email deletion failed";
        } else {
            errorMessage = null;
        }
        if (errorMessage != null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    @Transactional
    public void deleteApprovedEmail(CompanyApprovedEmails approvedEmail) {
        companyApprovedEmailsRepository.delete(approvedEmail);
        // jos poisto epäonnistui, heitä virhe
        if (companyApprovedEmailsRepository.findByEmail(approvedEmail.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Approved email deletion failed");
        }
    }

    @Transactional
    public void updateUserRole(User targetUser, Role role, CompanyApprovedEmails approvedEmail) {
        targetUser.setRole(role);
        userRepository.save(targetUser);
        approvedEmail.setRole(role);
        companyApprovedEmailsRepository.save(approvedEmail);
    }

    @Transactional
    public void updatePassword(User targetUser, String newPassword) {
        String encodedPassword = encoder.encode(newPassword);
        targetUser.setPassword(encodedPassword);
        expireAllTokens(targetUser.getEmail());
        userRepository.save(targetUser);
    }


    public LoginResponse login(String email, String password){

    Optional<User> userOptional = userRepository.findByEmail(email);

        if(userOptional.isPresent()) {
            User user = userOptional.get();
            if(encoder.matches(password, user.getPassword())) {
                String token = createAccessToken(user.getEmail(), user.getRole());
                String refreshToken = createRefreshToken(user.getEmail(), user.getRole());
                saveRefreshToken(user, refreshToken);  // refresh token tietokantaan
                LoginResponse response = new LoginResponse();
                response.setUserId(user.getId());
                response.setToken(token);
                response.setRole(user.getRole());
                response.setRefreshToken(refreshToken);
                response.setCompanyname(user.getCompany().getCompanyName());
                response.setCompanySettings(user.getCompany().getSettings());
                response.setFirstName(user.getFirstName());
                response.setLastName(user.getLastName());
                response.setEmail(user.getEmail());
                response.setPhoneNumber(user.getPhoneNumber());
                return response;
            }
            else {
                throw new IllegalArgumentException("Wrong password");
            }
        }
        else {
            throw new IllegalArgumentException("User not found");
        }
    }

    public Boolean isEmailApproved(String email) {
        return companyApprovedEmailsRepository.findByEmail(email).isPresent();
    }
    

    public String createAccessToken(String email, Role role) {
        Algorithm algorithm = Algorithm.HMAC256(jwtKey);
            return JWT.create()
            .withSubject(email)
            .withClaim("role", role.toString())
            .withExpiresAt(new Date(System.currentTimeMillis() + accessTokenExpirationTime)) 
            .sign(algorithm);
    }

    public String createRefreshToken(String email, Role role){
        Algorithm algorithm = Algorithm.HMAC256(jwtKey);
        return JWT.create()
            .withSubject(email)
            .withClaim("role", role.toString())
            .withExpiresAt(new Date(System.currentTimeMillis() + refreshTokenExpirationTime)) 
            .sign(algorithm);
    }

    public void saveRefreshToken(User user, String refreshToken){
        RefreshToken token = new RefreshToken();
        token.setToken(refreshToken);
        token.setUser(user);
        token.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationTime));  // Set expiry date
        refreshTokenRepository.save(token);
    }


    public String refreshAccessToken(String refreshToken) {
        // email tokenista
        String emailFromToken = verifyToken(refreshToken);
        if (emailFromToken == null || emailFromToken.isEmpty()) {
            throw new IllegalArgumentException("Invalid token: Token verification failed.");
        }
    
        // katso löytyykö refreshtoken tietokannasta
        RefreshToken confirmedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token: Token not found."));
    
        // katso onko token voimassa
        if (confirmedToken.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invalid token: Token expired.");
        }
    
        // katso löytyykö refreshtokenin email tietokannasta
        User user = userRepository.findByEmail(emailFromToken)
                .orElseThrow(() -> new IllegalArgumentException("User not found for the provided token."));
    
        // vertaa tokenin emailia ja tietokannan emailia
        if (!user.getEmail().equals(confirmedToken.getUser().getEmail())) {
            throw new IllegalArgumentException("Invalid token: Token does not match the logged-in user.");
        }
    
        // Generate a new access token
        return createAccessToken(user.getEmail(), user.getRole());
    }
    
    
    public void expireAllTokens(String email){
        Optional<User> userOptional = userRepository.findByEmail(email);
        if(userOptional.isPresent()) {
            User user = userOptional.get();
           // listaa kaikki käyttäjän refresh tokenit
            Optional<List<RefreshToken>> userTokens = refreshTokenRepository.findAllByUser(user);
            // jos löytyy, poista jokainen
            if(userTokens.isPresent()){
                for(RefreshToken token : userTokens.get()){
                    // poista token
                    refreshTokenRepository.delete(token);
                }
            }
            else{
                throw new IllegalArgumentException("User has no refresh tokens");
            }
            refreshTokenRepository.deleteRefreshTokenByUser(user);
        }
        else {
            throw new IllegalArgumentException("User not found");
        }
    }

    public void logout(User user){  // tarkista onko userilla refreshtoken, jos on, poista kaikki
       // listaa kaikki käyttäjän refreshtokenit
       Optional<List<RefreshToken>> userTokens = refreshTokenRepository.findAllByUser(user);
         if(userTokens.isPresent()){
             for(RefreshToken token : userTokens.get()){
                    refreshTokenRepository.delete(token);
             }
            }
        // jos ei löydy, älä tee mitään
    }

  

    public Role checkRoleFromToken(String token){
        Algorithm algorithm = Algorithm.HMAC256(jwtKey);
        Role role = Role.valueOf(JWT.require(algorithm).build().verify(token).getClaim("role").asString());
        return role;
    }

    public String verifyToken(String token){    // Toimii bearer prefixillä tai ilman
        Algorithm algo = Algorithm.HMAC256(jwtKey);
        JWTVerifier verifier = JWT.require(algo).build();
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            DecodedJWT decodedJWT = verifier.verify(token);
            return decodedJWT.getSubject();
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    public Role verifyTokenAndRole(String token){
        if(verifyToken(token)!=null){
            return checkRoleFromToken(token);
        }
        else{
            return null;
        }
    }

    public User getUserFromToken(String token) {
        String email = verifyToken(token);
       
        Optional<User> userOptional = userRepository.findByEmail(email);

        if(userOptional.isPresent()) {
            return userOptional.get();
        }
        else {
            throw new IllegalArgumentException("User not found");
        }
    }

    public Company getCompanyFromToken(String token) {
        String email = verifyToken(token);
        Optional<User> userOptional = userRepository.findByEmail(email);
        if(userOptional.isPresent()) {
            User user = userOptional.get();
            return user.getCompany();
        }
        else {
            throw new IllegalArgumentException("User not found");
        }
    }

    public String pairAddedEmailToCompanyWithToken(String token, String newEmail){
        String addersEmail = verifyToken(token);  // TODO: Tarkista rooli
        Optional<User> userOptional = userRepository.findByEmail(addersEmail);
        if(userOptional.isPresent()) {
            User user = userOptional.get();
            Company company = user.getCompany();
            CompanyApprovedEmails newApprovedEmail = new CompanyApprovedEmails();
            newApprovedEmail.setEmail(newEmail);
            newApprovedEmail.setCompany(company);
            companyApprovedEmailsRepository.save(newApprovedEmail);
            return "New email added & tied to company";
        } else {
            throw new IllegalArgumentException("User not found");
        }
    }

    public Boolean isSuperVisor(Role role){
        if(role == Role.MASTER || role == Role.SUPERVISOR){
            return true;
        }
        else{
            return false;
        }
    }

    public Boolean isMaster(Role role){
        if(role == Role.MASTER){
            return true;
        }
        else{
            return false;
        }
    }

    public void updateRole(Long userId,Role role) {
        User user = userRepository.findById(userId).orElse(null);
        user.setRole(role);
        userRepository.save(user);
    }


}

    


