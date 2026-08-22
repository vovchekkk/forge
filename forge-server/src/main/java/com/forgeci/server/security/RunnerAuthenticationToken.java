package com.forgeci.server.security;

import com.forgeci.server.entity.RunnerEntity;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;


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