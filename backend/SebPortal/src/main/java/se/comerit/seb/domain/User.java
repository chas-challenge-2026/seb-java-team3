package se.comerit.seb.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "password_md5")
    private String passwordHash;

    @Convert(converter = RoleConverter.class)
    @Column(name = "role", length = 20)
    private Role role;

    // JPA kräver en tom konstruktor. Hibernate använder den internt
    // (via reflection) för att bygga objekt från databasrader -
    // du kommer aldrig anropa den själv.
    protected User() {
    }

    // Det här är konstruktorn din egen kod faktiskt använder.
    public User(Long tenantId, String name, String email, String passwordHash, Role role) {
        this.tenantId = tenantId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
}