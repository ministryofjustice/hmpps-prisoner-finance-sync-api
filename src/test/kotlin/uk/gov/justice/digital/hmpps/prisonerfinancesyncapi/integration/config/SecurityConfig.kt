package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

  @Bean
  @Order(1)
  fun actuatorChain(http: HttpSecurity): SecurityFilterChain {
    http
      .securityMatcher("/actuator/**")
      .authorizeHttpRequests {
        it.anyRequest().permitAll()
      }
      .csrf { it.disable() }

    return http.build()
  }

  @Bean
  @Order(2)
  fun appChain(http: HttpSecurity): SecurityFilterChain {
    http
      .securityMatcher("/**")
      .authorizeHttpRequests {
        it.anyRequest().authenticated()
      }
      .oauth2ResourceServer {
        it.jwt { }
      }
      .csrf { it.disable() }

    return http.build()
  }

  @Bean
  fun debug(http: HttpSecurity): SecurityFilterChain {
    println("CUSTOM SECURITY CONFIG LOADED")
    return http.build()
  }
}
