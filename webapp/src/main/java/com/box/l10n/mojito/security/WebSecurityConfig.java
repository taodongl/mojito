package com.box.l10n.mojito.security;

import static org.springframework.security.web.server.authorization.IpAddressReactiveAuthorizationManager.hasIpAddress;

import com.box.l10n.mojito.ActuatorHealthLegacyConfig;
import com.box.l10n.mojito.service.security.user.UserService;
import com.google.common.base.Preconditions;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.ldap.LdapAuthenticationProviderConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * @author wyau
 */
@EnableWebSecurity
// TOOD(spring2) we don't use method level security, do we? remove?
@EnableMethodSecurity(securedEnabled = true, mode = AdviceMode.ASPECTJ)
@Configuration
public class WebSecurityConfig {

  static final String LOGIN_PAGE = "/login";

  /** logger */
  static Logger logger = LoggerFactory.getLogger(WebSecurityConfig.class);

  @Autowired SecurityConfig securityConfig;

  @Autowired LdapConfig ldapConfig;

  @Autowired ActiveDirectoryConfig activeDirectoryConfig;

  @Autowired ActuatorHealthLegacyConfig actuatorHealthLegacyConfig;

  @Autowired UserDetailsContextMapperImpl userDetailsContextMapperImpl;

  @Autowired(required = false)
  @Qualifier("oauth2Filter")
  Filter oauth2Filter;

  @Autowired(required = false)
  RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter;

  @Autowired(required = false)
  PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider;

  @Autowired UserService userService;

  @Autowired
  public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
    for (SecurityConfig.AuthenticationType authenticationType :
        securityConfig.getAuthenticationType()) {
      switch (authenticationType) {
        case DATABASE:
          configureDatabase(auth);
          break;
        case LDAP:
          configureLdap(auth);
          break;
        case AD:
          configureActiveDirectory(auth);
          break;
        case HEADER:
          configureHeaderAuth(auth);
          break;
      }
    }
  }

  void configureActiveDirectory(AuthenticationManagerBuilder auth) throws Exception {
    logger.debug("Configuring in active directory authentication");
    ActiveDirectoryAuthenticationProviderConfigurer<AuthenticationManagerBuilder>
        activeDirectoryManagerConfigurer = new ActiveDirectoryAuthenticationProviderConfigurer<>();

    activeDirectoryManagerConfigurer.domain(activeDirectoryConfig.getDomain());
    activeDirectoryManagerConfigurer.url(activeDirectoryConfig.getUrl());
    activeDirectoryManagerConfigurer.rootDn(activeDirectoryConfig.getRootDn());
    activeDirectoryManagerConfigurer.userServiceDetailMapper(userDetailsContextMapperImpl);

    auth.apply(activeDirectoryManagerConfigurer);
  }

  void configureDatabase(AuthenticationManagerBuilder auth) throws Exception {
    logger.debug("Configuring in database authentication");
    auth.userDetailsService(getUserDetailsService()).passwordEncoder(new BCryptPasswordEncoder());
  }

  void configureLdap(AuthenticationManagerBuilder auth) throws Exception {
    logger.debug("Configuring ldap server");
    LdapAuthenticationProviderConfigurer<AuthenticationManagerBuilder>.ContextSourceBuilder
        contextSourceBuilder =
            auth.ldapAuthentication()
                .userSearchBase(ldapConfig.getUserSearchBase())
                .userSearchFilter(ldapConfig.getUserSearchFilter())
                .groupSearchBase(ldapConfig.getGroupSearchBase())
                .groupSearchFilter(ldapConfig.getGroupSearchFilter())
                .groupRoleAttribute(ldapConfig.getGroupRoleAttribute())
                .userDetailsContextMapper(userDetailsContextMapperImpl)
                .contextSource();

    if (ldapConfig.getPort() != null) {
      contextSourceBuilder.port(ldapConfig.getPort());
    }

    contextSourceBuilder
        .root(ldapConfig.getRoot())
        .url(ldapConfig.getUrl())
        .managerDn(ldapConfig.getManagerDn())
        .managerPassword(ldapConfig.getManagerPassword())
        .ldif(ldapConfig.getLdif());
  }

  void configureHeaderAuth(AuthenticationManagerBuilder auth) {
    Preconditions.checkNotNull(
        preAuthenticatedAuthenticationProvider,
        "The preAuthenticatedAuthenticationProvider must be configured");
    logger.debug("Configuring in pre authentication");
    auth.authenticationProvider(preAuthenticatedAuthenticationProvider);
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    logger.debug("Configuring web security");

    // TODO should we just enable caching of static assets, this disabling cache control for
    // everything
    // https://docs.spring.io/spring-security/site/docs/current/reference/html5/#headers-cache-control
    http.headers(headers -> headers.cacheControl(HeadersConfigurer.CacheControlConfig::disable));

    // no csrf on rotation end point - they are accessible only locally
    http.csrf(
        csrf ->
            csrf.ignoringRequestMatchers(
                "/actuator/shutdown", "/actuator/loggers/**", "/api/rotation"));

    // matcher order matters - "everything else" mapping must be last
    // https://stackoverflow.com/questions/72366267/matching-ip-address-with-authorizehttprequests
    http.authorizeHttpRequests(
        authorizeRequests ->
            authorizeRequests
                .requestMatchers(
                    "/intl/*",
                    "/img/*",
                    "/login/**",
                    "/favicon.ico",
                    "/fonts/*",
                    "/cli/**",
                    "/js/**",
                    "/css/**")
                .permitAll()
                . // always accessible to serve the frontend
                requestMatchers(getHealthcheckPatterns())
                .permitAll()
                . // allow health entry points
                requestMatchers("/actuator/shutdown", "/actuator/loggers/**", "/api/rotation")
                .access(hasIpAddress("127.0.0.1"))
                . // local access only for rotation management and logger config
                requestMatchers("/**")
                .authenticated() // everything else must be authenticated
        );

    logger.debug("For APIs, we don't redirect to login page. Instead we return a 401");
    http.exceptionHandling(
        exceptionHandling ->
            exceptionHandling.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                new AntPathRequestMatcher("/api/*")));

    if (securityConfig.getUnauthRedirectTo() != null) {
      logger.debug(
          "Redirect to: {} instead of login page on authorization exceptions",
          securityConfig.getUnauthRedirectTo());
      http.exceptionHandling(
          exceptionHandling ->
              exceptionHandling.defaultAuthenticationEntryPointFor(
                  new LoginUrlAuthenticationEntryPoint(securityConfig.getUnauthRedirectTo()),
                  new AntPathRequestMatcher("/*")));
    }

    for (SecurityConfig.AuthenticationType authenticationType :
        securityConfig.getAuthenticationType()) {
      switch (authenticationType) {
        case HEADER -> {
          Preconditions.checkNotNull(
              requestHeaderAuthenticationFilter,
              "The requestHeaderAuthenticationFilter must be configured");
          logger.debug("Add request header Auth filter");
          //
          // requestHeaderAuthenticationFilter.setAuthenticationManager(authenticationManager());
          http.addFilterBefore(requestHeaderAuthenticationFilter, BasicAuthenticationFilter.class);
        }
        case OAUTH2 -> {
          logger.debug("Configure OAuth2");
          http.oauth2Login(
              oauth2Login -> {
                oauth2Login.loginPage(LOGIN_PAGE);

                oauth2Login.authorizationEndpoint(
                    authorizationEndpointConfig ->
                        authorizationEndpointConfig.baseUri(
                            LOGIN_PAGE
                                + OAuth2AuthorizationRequestRedirectFilter
                                    .DEFAULT_AUTHORIZATION_REQUEST_BASE_URI));

                oauth2Login.userInfoEndpoint(
                    userInfoEndpoint -> {
                      userInfoEndpoint.userService(
                          new UserDetailImplOAuth2UserService(securityConfig, userService));
                    });
              });
        }
        case AD, LDAP, DATABASE -> {
          logger.debug("Configure form login for DATABASE, AD or LDAP");
          http.formLogin(
              formLogin ->
                  formLogin
                      .loginPage(LOGIN_PAGE)
                      .successHandler(new ShowPageAuthenticationSuccessHandler()));
        }
      }
    }
    return http.build();
  }

  /**
   * Returns health entry points.
   *
   * <p>By default it is only the actuator but potentially include the legacy entry point. {@link
   * com.box.l10n.mojito.rest.rotation.ActuatorHealthLegacyWS}
   *
   * @return
   */
  String[] getHealthcheckPatterns() {
    List<String> patterns = new ArrayList<>();
    patterns.add("/actuator/health");
    if (actuatorHealthLegacyConfig.isForwarding()) {
      patterns.add("/health");
    }
    return patterns.toArray(new String[0]);
  }

  @Primary
  @Bean
  protected UserDetailsServiceImpl getUserDetailsService() {
    return new UserDetailsServiceImpl();
  }

  private static AuthorizationManager<RequestAuthorizationContext> hasIpAddress(String ipAddress) {
    IpAddressMatcher ipAddressMatcher = new IpAddressMatcher(ipAddress);
    return (authentication, context) -> {
      HttpServletRequest request = context.getRequest();
      return new AuthorizationDecision(ipAddressMatcher.matches(request));
    };
  }
}
