package com.forgeci.server.security;

import com.forgeci.server.entity.RunnerEntity;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication for a runner principal. The principal is the {@link RunnerEntity}
 * loaded from the credential hash, so services can read its owner id directly.
 */
public class RunnerAuthenticationToken extends AbstractAuthenticationToken {

    private final RunnerEntity runner;

    public RunnerAuthenticationToken(RunnerEntity runner, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.runner = runner;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return runner;
    }
}