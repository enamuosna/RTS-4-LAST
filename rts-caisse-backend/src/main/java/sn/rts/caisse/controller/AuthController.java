    package sn.rts.caisse.controller;

    import io.swagger.v3.oas.annotations.Operation;
    import io.swagger.v3.oas.annotations.security.SecurityRequirements;
    import io.swagger.v3.oas.annotations.tags.Tag;
    import jakarta.servlet.http.HttpServletRequest;
    import jakarta.validation.Valid;
    import lombok.RequiredArgsConstructor;
    import org.springframework.http.ResponseEntity;
    import org.springframework.security.core.AuthenticationException;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestBody;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RestController;
    import sn.rts.caisse.dto.auth.AuthResponse;
    import sn.rts.caisse.dto.auth.LoginRequest;
    import sn.rts.caisse.security.ClientIpUtil;
    import sn.rts.caisse.security.LoginRateLimitService;
    import sn.rts.caisse.security.TooManyLoginAttemptsException;
    import sn.rts.caisse.service.AuthService;

    @RestController
    @RequestMapping("/api/auth")
    @RequiredArgsConstructor
    @Tag(name = "Authentification", description = "Connexion et émission de JWT")
    @SecurityRequirements // endpoints publics
    public class AuthController {

        private final AuthService authService;
        private final LoginRateLimitService rateLimit;

        @PostMapping("/login")
        @Operation(summary = "Authentifier un utilisateur et retourner un JWT",
                   description = "Rate limit : 5 echecs max par IP sur une fenetre "
                           + "glissante de 15 minutes. Reponse 429 + Retry-After "
                           + "au-dela.")
        public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                                  HttpServletRequest httpRequest) {
            final String clientIp = ClientIpUtil.get(httpRequest);

            // 1) Pre-check : l'IP est-elle deja bloquee ?
            rateLimit.checkBlocked(clientIp).ifPresent(secondsToWait -> {
                throw new TooManyLoginAttemptsException(secondsToWait);
            });

            // 2) Tentative d'authentification
            try {
                AuthResponse response = authService.login(request);
                // 3a) Succes : on remet le compteur a zero pour cette IP
                rateLimit.recordSuccess(clientIp);
                return ResponseEntity.ok(response);
            } catch (AuthenticationException e) {
                // 3b) Echec : on incremente le compteur, l'exception remonte
                //     au GlobalExceptionHandler qui repondra 401.
                rateLimit.recordFailure(clientIp);
                throw e;
            }
        }
    }
