package com.example.bi.service.identity;

import com.example.bi.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class SecurityUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String displayName;
    private final String password;
    private final boolean enabled;
    private final Role role;
    private final Collection<? extends GrantedAuthority> authorities;

    public SecurityUser(UUID id, String email, String displayName, String password, boolean enabled, Role role) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.password = password;
        this.enabled = enabled;
        this.role = role != null ? role : Role.ANALYST;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

