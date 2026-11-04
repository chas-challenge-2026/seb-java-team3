package se.comerit.seb.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.comerit.seb.dto.ChangeUserRoleRequest;
import se.comerit.seb.dto.CreateUserRequest;
import se.comerit.seb.dto.UserResponse;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.service.UserAdminService;

import java.util.List;

// Användaradministration för ADMIN (ADR 0017). Rollen kontrolleras både här och i UserAdminService.
// Företaget kommer alltid från admins token: varken listan eller en ny användare kan styras med ett
// företags-id från anropet.
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final JwtUserContext jwtUserContext;

    public AdminUserController(UserAdminService userAdminService, JwtUserContext jwtUserContext) {
        this.userAdminService = userAdminService;
        this.jwtUserContext = jwtUserContext;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<UserResponse> listUsers() {
        AuthenticatedUserContext admin = jwtUserContext.requireAuthenticated();
        return userAdminService.listUsers(admin);
    }

    // 201 med den nya användarens visningsuppgifter. 400 vid ogiltiga uppgifter, 403 för andra än ADMIN
    // och 409 om e-postadressen redan används.
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        AuthenticatedUserContext admin = jwtUserContext.requireAuthenticated();
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdminService.createUser(admin, request));
    }

    // 200 med användaren och den nya rollen. 400 vid en ogiltig roll, 403 för andra än ADMIN och när målet är en
    // administratör, 404 om användaren inte finns i företaget och 409 om en attestant har väntande steg.
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{userId}/role")
    public UserResponse changeRole(@PathVariable Long userId, @RequestBody ChangeUserRoleRequest request) {
        AuthenticatedUserContext admin = jwtUserContext.requireAuthenticated();
        return userAdminService.changeRole(admin, userId, request);
    }

    // 204 utan innehåll. 403 för andra än ADMIN och när målet är en administratör, 404 om användaren inte finns
    // i företaget och 409 om användaren har betalningar eller atteststeg kopplade till sig (historiken bevaras).
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        AuthenticatedUserContext admin = jwtUserContext.requireAuthenticated();
        userAdminService.deleteUser(admin, userId);
        return ResponseEntity.noContent().build();
    }
}
