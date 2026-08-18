package com.example.banhangtructuyen.infrastructure.security;

import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/** Loads a {@link Customer} by email and adapts it to Spring Security's {@link UserDetails}. */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    @Override
    public UserDetails loadUserByUsername(final String email) {
        final Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for email: " + email));

        return User.builder()
                .username(customer.getEmail())
                .password(customer.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + customer.getRole().name())))
                .disabled(customer.getStatus() != Customer.CustomerStatus.ACTIVE)
                .build();
    }
}
