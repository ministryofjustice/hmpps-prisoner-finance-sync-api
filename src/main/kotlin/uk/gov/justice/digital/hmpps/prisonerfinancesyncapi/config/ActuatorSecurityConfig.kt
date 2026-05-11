package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer

@Configuration
@EnableWebSecurity
class ActuatorSecurityConfig {

  @Bean
  fun webSecurityCustomizer(): WebSecurityCustomizer = WebSecurityCustomizer { web ->
    web.ignoring().requestMatchers("/metrics")
  }
}
