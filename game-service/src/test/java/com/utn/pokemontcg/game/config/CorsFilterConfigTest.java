package com.utn.pokemontcg.game.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorsFilterConfigTest {

    private final CorsFilterConfig config = new CorsFilterConfig();

    @Test
    void corsFilterRegistrationIsCreated() {
        FilterRegistrationBean<Filter> bean = config.corsFilterRegistration();
        assertNotNull(bean);
        assertNotNull(bean.getFilter());
    }

    @Test
    void doFilterSetsCorsHeadersWithOrigin() throws IOException, ServletException {
        FilterRegistrationBean<Filter> bean = config.corsFilterRegistration();
        Filter filter = bean.getFilter();

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Origin")).thenReturn("http://localhost:4200");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        verify(response).setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        verify(response).setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Requested-With, Accept, Origin");
        verify(response).setHeader("Access-Control-Allow-Credentials", "true");
        verify(response).setHeader("Access-Control-Max-Age", "3600");
        verify(response).setHeader("Vary", "Origin");
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterSetsWildcardOriginWhenOriginIsNull() throws IOException, ServletException {
        FilterRegistrationBean<Filter> bean = config.corsFilterRegistration();
        Filter filter = bean.getFilter();

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Access-Control-Allow-Origin", "*");
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterSetsWildcardOriginWhenOriginIsBlank() throws IOException, ServletException {
        FilterRegistrationBean<Filter> bean = config.corsFilterRegistration();
        Filter filter = bean.getFilter();

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Origin")).thenReturn("   ");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Access-Control-Allow-Origin", "*");
    }

    @Test
    void doFilterShortCircuitsOnOptionsRequest() throws IOException, ServletException {
        FilterRegistrationBean<Filter> bean = config.corsFilterRegistration();
        Filter filter = bean.getFilter();

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Origin")).thenReturn("http://localhost:4200");
        when(request.getMethod()).thenReturn("OPTIONS");

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(chain, never()).doFilter(any(), any());
    }
}
