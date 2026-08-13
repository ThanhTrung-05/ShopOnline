package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByCustomerIdOrderByAddressIdAsc(Long customerId);

    Optional<Address> findByAddressIdAndCustomerId(Long addressId, Long customerId);

    long countByCustomerId(Long customerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Address a SET a.defaultAddress = false WHERE a.customerId = :customerId AND a.defaultAddress = true")
    void clearDefaultForCustomer(@Param("customerId") Long customerId);

    @Query("SELECT MIN(a.addressId) FROM Address a WHERE a.customerId = :customerId")
    Optional<Long> findMinAddressIdByCustomerId(@Param("customerId") Long customerId);
}
