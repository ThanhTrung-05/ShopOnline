package com.example.banhangtructuyen.domain.repository;

import com.example.banhangtructuyen.domain.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
}
